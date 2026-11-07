# 마중 통신 규격

이 문서는 차 앱·서버·폰 앱이 따라야 하는 통신 계약의 기준 문서다. 메시지 형식·필드·오류·세션 규칙을 변경하거나 추가할 때는 이 문서를 먼저 수정하고 구현을 맞춘다. 구현과 문서가 다르면 계약과 구현의 차이를 확인하고, 합의한 계약을 이 문서에 먼저 반영한 뒤 코드를 수정한다.

변경 순서는 **`PROTOCOL.md` → `protocol/` 모듈 → `server/` → 차 앱 `app/` → 폰 앱 `phone/`** 이다. P3에서도 기존 계약을 우선 사용하고, 메시지 변경·추가가 필요하면 같은 순서를 따른다. 현재 폰 작업에서는 `app/`·`server/` 코드를 수정하지 않으며, 해당 코드 변경이 필요하면 사용자에게 필요한 변경과 영향을 먼저 알린다.

**구현 상태(2026-11-07, 기존 HEAD `74821e3`와 작업 중인 P2 변경)**: 서버·공유 `protocol` 모듈·차 앱은 구현돼 있다. 탑승자용 `phone/`은 P2 디버그 오프라인 UI 구현·debug/폰 AVD 검증을 완료했으며, 이 문서의 폰 방향 메시지는 서버가 제공하는 계약이다. 실제 폰 REST·WebSocket·토큰 보관·재연결은 P3, GPS·위치 서비스는 P4 미구현이다. [PHONE_APP.md](PHONE_APP.md)에서 P2 미리보기와 미래 연동 설계를 분리한다. 실제 통신의 탑승자 대역은 `scripts/fake-rider.mjs`다.

- 전송: HTTPS(REST) + WebSocket(텍스트 프레임, JSON). STOMP는 쓰지 않는다.
- 인코딩: UTF-8 JSON. 필드 이름은 camelCase.
- 시각: Unix epoch 밀리초(`Long`).
- 좌표: WGS84 위경도(`Double`), 정확도는 미터(`Float`).
- 기본 URL: 차 앱의 `BuildConfig.SERVER_URL`. debug는 `http://10.0.2.2:8080`(에뮬레이터에서 호스트 PC), release는 Gradle 속성 `-Pmaejung.serverUrl=…` 우선, 없으면 `local.properties`의 같은 속성이다. 주소가 없으면 release 빌드는 실패한다. Railway 실제 배포·주소 확정은 아직 진행하지 않았다.

## 1. 신원과 토큰

로그인은 없다. 차와 탑승자는 서버가 서명한 토큰으로 구분한다.

| 토큰 | 발급 | 페이로드 | 보관 |
|---|---|---|---|
| vehicleToken | `POST /api/vehicles` | `{"k":"v","vid":"v_…"}` | 차 앱 SharedPreferences |
| riderToken | `POST /api/pairings` | `{"k":"r","vid":"v_…","rid":"r_…"}` | 현재 대역 스크립트 실행 메모리. 폰 SharedPreferences 보관은 미구현 설계 |

- 형식: `base64url(payloadJson) + "." + base64url(HMAC-SHA256(secret, payloadJson))`. 비밀키는 서버 설정 `maejung.token-secret`(환경변수 `MAEJUNG_TOKEN_SECRET`).
- 토큰에 이름 같은 개인정보를 넣지 않는다(WebSocket 주소의 쿼리로 가서 프록시 로그에 남을 수 있다). 탑승자 이름은 요청 클라이언트가 `pickup.request`에 실어 보낸다.
- 서버는 토큰·차량 목록·가족 연결 목록을 저장하지 않고 서명을 검증한다. **서버 비밀키가 같고 클라이언트가 토큰을 보관하면 재시작 후에도 같은 페어링을 사용할 수 있다.** 진행 중 세션과 미사용 연결 코드는 메모리에만 있어서 재시작하면 사라진다(5절). 차 앱의 토큰·가족 목록은 로컬 SharedPreferences에 남는다.
- 토큰에는 만료 시각이 없고, 개별 폐기·페어링 해제 API도 없다. 비밀키를 바꾸면 이전 키로 서명한 토큰이 모두 무효가 된다.

## 2. REST API

아래 API의 응답은 JSON. 컨트롤러에서 처리하는 오류는 HTTP 상태 코드 + `{"code": "...", "message": "..."}`다. 이 형식을 모든 Spring 기본 오류 응답에 일반화하지 않는다.

공통 오류:

| 상태 | code | 조건 |
|---|---|---|
| 401 | `INVALID_TOKEN` | `/api/pairing-codes`의 Authorization 헤더가 없거나, 토큰 형식·서명·종류가 틀림 |
| 400 | `BAD_REQUEST` | 요청 본문을 읽을 수 없음(잘못된 JSON 등). 필드 누락은 아래 개별 API 규칙을 따름 |

### `POST /api/vehicles` — 차 등록

인증·요청 본문 없음. 차 앱은 저장한 토큰이 없을 때 호출하며, 4401을 받은 뒤에도 재등록한다. 실패하면 5초 간격으로 다시 시도한다.

응답 `201`:
```json
{ "vehicleId": "v_7f3a1b2c4d5e", "vehicleToken": "…" }
```

### `POST /api/pairing-codes` — 연결 코드 발급

차 화면의 "가족 연결" 버튼. 헤더 `Authorization: Bearer <vehicleToken>`. 현재 서버는 `Bearer ` 접두사를 제거한 후 검증하므로 헤더에 토큰만 넣어도 처리하지만, 차 앱은 Bearer 형식을 사용한다.

응답 `201`:
```json
{ "code": "482913", "expiresAt": 1762412400000 }
```

- 6자리 숫자(앞자리 0 허용, 문자열). 유효 10분, 한 번 쓰면 사라진다. 서버 메모리에만 둔다.
- 발급할 때 유효한 코드와 겹치면 다시 뽑는다.
- 같은 차가 새 코드를 받으면 그 차의 이전 코드는 무효가 된다.

### `POST /api/pairings` — 폰에서 연결

요청:
```json
{ "code": "482913", "riderName": "민지" }
```

응답 `201`:
```json
{ "riderId": "r_91c2aa0b7e31", "vehicleId": "v_7f3a1b2c4d5e", "riderToken": "…" }
```

오류: `404 PAIRING_CODE_NOT_FOUND`(코드 누락·null·불일치·만료·이미 사용), `400 INVALID_RIDER_NAME`(이름 누락·null 또는 `trim()` 후 Kotlin `String.length`가 1~20이 아님). 길이는 UTF-16 코드 단위로 세며 내부 공백은 유지한다. 이름을 먼저 검사하므로 이름과 코드가 모두 잘못되면 `INVALID_RIDER_NAME`이다. P2 폰 입력도 같은 이름 규칙과 ASCII 숫자(`0`~`9`) 정확히 6자리 코드를 검사하는 범위이며, 서버로 보내거나 코드의 유효성을 확인하지 않는다.

- 차가 WebSocket에 붙어 있으면 `pairing.completed`를 보낸다(3.3). 차가 오프라인이어도 페어링 REST는 성공하며, 이 알림을 나중에 재전송하거나 가족 목록을 조회하는 API는 없다.
- 이름은 토큰에 넣지 않는다. 요청자는 `pickup.request`마다 `riderName`을 보낸다. 서버는 이를 다시 trim·검사하고 세션에 담는다. 폰 앱의 이름 영구 보관은 아직 구현하지 않았다.

### `GET /health`

`200 {"status":"ok"}`. 구현된 헬스체크이며 Railway에도 이 경로를 지정할 예정이다. Railway에서의 응답 확인은 아직 하지 않았다.

## 3. WebSocket

- 주소: `wss://<host>/ws?token=<vehicleToken 또는 riderToken>` (로컬은 `ws://`).
- 서버는 핸드셰이크를 그대로 받아들이고, `afterConnectionEstablished`에서 토큰을 검증한다. 틀리면 **close code 4401**로 닫는다(HandshakeInterceptor에서 거절하면 클라이언트가 HTTP 응답만 받아 구분이 어렵기 때문).
- 한 토큰 주인(principal)당 연결 하나. 새 연결이 오면 이전 연결을 **4409**로 닫는다.
- 현재 차 앱의 재연결 동작(`WebSocketTransport`·`CarSessionManager`):
  - OkHttp `pingInterval(15s)`.
  - 일반 끊김·`onFailure`: 1, 2, 4, 8, 이후 최대 15초 간격으로 재연결한다. 연결 성공 시 재시도 횟수를 초기화한다. 명시적 `close()` 뒤에는 자동 재연결하지 않는다.
  - **4401이면 해당 토큰으로 자동 재연결하지 않는다.** transport는 `UNAUTHORIZED`를 내보내고, manager가 차 토큰·로컬 가족 목록을 지운 뒤 새 차 등록을 시도해 새 토큰으로 연결한다. 가족 연결은 다시 해야 한다.
  - 4401은 비밀키 변경뿐 아니라 토큰 누락·손상·서명 오류에도 발생한다. 키를 바꿨다고 이미 연결된 소켓을 즉시 끊는 기능은 없으며 다음 연결 검증 때 적용된다.
  - **4409면 `REPLACED`가 되고 자동 재연결하지 않는다.** 같은 principal의 새 연결이 이전 연결을 대체한 것이며 같은 기기인지는 서버가 구분하지 않는다.
- 폰의 토큰 삭제·연결 화면 전환·재연결 정책은 미구현 설계다. 대역 스크립트는 토큰을 영구 저장하거나 재연결하지 않고 소켓이 닫히면 종료한다.
- WebSocket 연결 해제 자체가 세션을 즉시 끝내지는 않는다. 서버는 연결 레지스트리만 비우고 세션은 4절 만료 규칙으로 정리한다.
- 메시지 공통 형식:

```json
{ "type": "pickup.request", "ts": 1762412400000, "payload": { "riderName": "민지" } }
```

- **서버 → 클라이언트**의 진행 중 세션 상태·요청·중계·종료 메시지에는 `sessionId`가 들어 있다. `session.state none`, `pairing.completed`, `error`에는 없다(`SESSION_NOT_FOUND` 같은 세션 오류도 포함).
- **클라이언트 → 서버** 메시지의 `sessionId`는 넣지 않아도 된다. 서버는 보낸 사람(vehicleId 또는 riderId)의 진행 중 세션을 인덱스로 찾고, 클라이언트가 보낸 `sessionId`는 무시한다.
- 데이터가 없는 메시지도 인코더는 `payload: {}`를 보낸다. 서버 `Envelope`는 `payload` 생략을 빈 Map, `ts` 생략을 0으로 읽는다. `ts`는 참고용이며 서버 만료 판단은 주입된 `Clock`의 수신 시각을 사용한다.
- 아래 payload 표는 공유 DTO가 표현하는 형식이다. 서버가 모든 필드·숫자 범위·문자열 값을 검증하는 것은 아니다(3.5). 공통 `Protocol.decode`는 모르는 필드를 무시하고, 모르는 type은 `Unknown`, 읽을 수 없는 메시지는 null로 반환한다. 차 transport는 null을 로그에만 남긴다.

### 3.1 폰 → 서버

| type | payload | 설명 |
|---|---|---|
| `pickup.request` | `{ "riderName" }` | "데리러 와줘" |
| `location.update` | `{ "lat", "lng", "accuracy" }` | SHARING에서만 처리하고, 다른 상태에서는 조용히 버린다 |
| `session.end` | `{ "reason": "manual" \| "app_closed" }` | 요청 취소(REQUESTED) 또는 공유 종료(SHARING) |

### 3.2 차 → 서버

| type | payload | 설명 |
|---|---|---|
| `pickup.accept` | `{}` | 승인 |
| `pickup.reject` | `{}` | 거절 |
| `location.update` | `{ "lat", "lng", "accuracy" }` | 폰과 같음 |
| `eta.update` | `{ "seconds", "meters", "source": "navi" \| "estimate" }` | 첫 값, 30초 경과, seconds가 직전 전송 대비 20% 초과 변경, source 변경 시. 차는 3초 주기로 검사. SHARING에서만 중계 |
| `vehicle.light` | `{ "on": true \| false }` | Nearby 진입 시 ON, Nearby 이탈 중 공유가 계속되면 원래 값 복구를 알림. 세션 종료 시의 로컬 복구는 보내지 않음. SHARING에서만 처리 |
| `session.boarded` | `{}` | 차가 탑승을 판정했다(DESIGN.md 3절). SHARING에서만 |
| `session.end` | `{ "reason": "manual" }` | 차에서 수동 종료 |

**위치 heartbeat 구현**: 차는 Sharing·Nearby 동안 마지막으로 받은 위치가 있으면 **3초마다** `location.update`를 보낸다. 10m 이동 조건을 따로 검사하지 않고, 위치를 한 번도 못 얻었으면 보내지 않는다. 서버는 좌표 시각·이동 여부를 검사하지 않아 같은 마지막 좌표도 수신 시각을 갱신한다. 대역 스크립트는 승인 후 2초 간격으로 15단계 접근 좌표를 보내고 도착 좌표를 계속 보낸다. 폰 앱의 "3초 또는 10m, 최소 10초"는 아직 설계다.

### 3.3 서버 → 차

| type | payload | 설명 |
|---|---|---|
| `session.state` | 5절 | 연결 직후 항상 한 번 |
| `pickup.requested` | `{ "riderId", "riderName" }` | 새 요청. 차 앱은 알림과 음성을 띄운다 |
| `peer.location` | `{ "lat", "lng", "accuracy" }` | 탑승자 위치 |
| `session.ended` | `{ "reason" }` | 세션 종료(이유는 4절) |
| `pairing.completed` | `{ "riderId", "riderName" }` | 새 가족이 연결됨 |
| `error` | `{ "code", "message" }` | |

### 3.4 서버 → 폰

| type | payload | 설명 |
|---|---|---|
| `session.state` | 5절 | 연결 직후 항상 한 번 |
| `pickup.pending` | `{}` | 요청이 차에 전달됨. 여기서 sessionId를 처음 받는다 |
| `pickup.accepted` | `{}` | 승인됨. 위치 전송을 시작한다 |
| `peer.location` | `{ "lat", "lng", "accuracy" }` | 차 위치 |
| `peer.eta` | `{ "seconds", "meters", "source" }` | 차가 보낸 `eta.update` payload 그대로 |
| `peer.light` | `{ "on" }` | 서버가 `on == true`로 변환한 불리언. 누락·다른 값은 false |
| `session.ended` | `{ "reason" }` | 세션 종료. 거절도 이걸로 온다(`reason: "rejected"`) |
| `error` | `{ "code", "message" }` | |

### 3.5 WebSocket 오류 코드와 검증 범위

아래 오류 코드는 서버 구현이다. "폰 처리"는 [PHONE_APP.md](PHONE_APP.md)의 미구현 설계이며, 대역 스크립트는 모든 `error`에서 소켓을 닫는다.

| code | 의미 | 받는 쪽 처리 |
|---|---|---|
| `SESSION_ALREADY_ACTIVE` | 이 탑승자 또는 이 차에 진행 중 세션이 있다 | 폰: "이미 다른 요청이 진행 중이에요" 후 Idle |
| `VEHICLE_OFFLINE` | 차가 연결돼 있지 않아 요청을 전달하지 못했다. 세션은 만들지 않는다 | 폰: "차가 꺼져 있어요" 후 Idle |
| `SESSION_NOT_FOUND` | 세션 없는 차의 accept·reject·boarded·end 또는 세션 없는 탑승자의 end | 폰 설계: 로그만 |
| `INVALID_STATE` | 차의 accept·reject가 REQUESTED가 아님, boarded가 SHARING이 아님 | 폰 설계: 로그만 |
| `BAD_MESSAGE` | 봉투 JSON 파싱 실패, 역할에 허용되지 않은 type, trim 후 길이가 1~20자가 아닌 `pickup.request.riderName` | 폰 설계: 로그만 |

- 탑승자 이름 검사 → 차 온라인 검사 → 차·탑승자 세션 중복 검사 순서다. 차가 오프라인이면 기존 세션 여부보다 `VEHICLE_OFFLINE`이 우선한다.
- `location.update`는 세션이 없거나 SHARING이 아니면 양쪽 모두 조용히 버린다. 차의 `eta.update`·`vehicle.light`도 같다.
- SHARING의 위치는 `lat`·`lng`·`accuracy`를 꺼내 중계할 뿐 필수 여부·타입·범위를 검사하지 않는다. ETA는 payload 그대로 중계하며 `source` 값도 검증하지 않는다. 잘못된 payload가 항상 `BAD_MESSAGE`가 되는 계약은 아니다.
- `session.end`는 reason을 읽지 않고 항상 `manual`로 끝낸다. `manual`·`app_closed`는 공유 DTO·설계에서 쓰는 값이다.
- 차 앱 상태 머신은 `error`로 상태를 바꾸지 않는다. 예를 들어 로컬에서 수락해 Sharing으로 간 뒤 서버가 `SESSION_NOT_FOUND`를 보내도 이 오류만으로는 정리되지 않는다.

## 4. 세션 수명 (서버 기준)

서버가 메모리에 보관하는 상태값은 **REQUESTED와 SHARING 두 가지**다. 아래 ENDED는 종료·삭제를 나타내는 설명용 표기이며 `SessionState` enum 값이나 보관 이력이 아니다. 차 앱의 Nearby는 서버가 모른다.

```
pickup.request ─► REQUESTED ─pickup.accept─► SHARING ─session.boarded / session.end / 만료─► ENDED
                      │
                      └─pickup.reject / session.end / 5분 무응답─► ENDED
```

| 종료 이유(`reason`) | 조건 |
|---|---|
| `boarded` | 차가 `session.boarded` |
| `manual` | 어느 쪽이든 `session.end` (`app_closed`도 `manual`로 통일해 알린다) |
| `rejected` | 차가 `pickup.reject` |
| `request_timeout` | REQUESTED가 된 지(createdAt) 5분 이상 |
| `peer_lost` | SHARING에서 어느 한쪽의 마지막 위치 수신 시각이 2분 넘게 지남. 위치를 한 번도 안 보낸 쪽은 accept 시각을 기준으로 센다 |
| `max_duration` | createdAt부터 60분 이상(승인 시각부터가 아님) |

- 만료는 `SessionSweeper`가 `fixedDelay = 5000ms`로 검사한다. `max_duration` → `request_timeout` → `peer_lost` 순서로 판정하며, 임계 시각에 즉시 종료된다고 보장하지 않는다.
- 종료되면 서버는 세션과 차량·탑승자 인덱스, 마지막 위치 수신 시각을 지우고, 연결된 양쪽에 `session.ended`를 보낸다. **좌표는 세션에 저장한 적이 없고 중계만 한다.** 거절도 양쪽에 `session.ended(rejected)` 하나만 보낸다.
- 차 하나에 진행 중 세션은 하나만. 같은 차의 다른 탑승자가 요청하면 그 폰에 `error SESSION_ALREADY_ACTIVE`.
- 서버는 중계 위치를 디스크나 애플리케이션 로그에 기록하지 않는다. 수신 로그에는 type·sessionId·principal 종류(v/r), 종료 로그에는 sessionId·reason, 송신 실패에는 type·대상 종류 접두사가 남는다. 대역 스크립트는 개발용으로 좌표를 콘솔에 출력한다.
- 오프라인 수신자에게 메시지를 보관·재전송하지 않는다. 재연결 때 최신 위치·ETA·실내등·종료 이유도 재생하지 않고 아래 상태만 보낸다.

## 5. 연결 직후 상태 동기화 (`session.state`)

재연결, 앱 재시작, 서버 재시작 뒤에 양쪽 상태를 맞추기 위해, 서버는 WebSocket 연결(토큰 검증 통과) 직후 항상 `session.state`를 보낸다.

```json
{ "type": "session.state", "sessionId": "s_…", "ts": 1762412400000,
  "payload": { "state": "sharing", "riderId": "r_…", "riderName": "민지" } }
```

- state는 `none`·`requested`·`sharing`이다. `none`이면 봉투의 `sessionId`와 payload의 `riderId`·`riderName`을 생략한다.
- 현재 차 앱 처리:
  - Requested·Sharing·Nearby에서 `none`을 받으면 **Ended(peer_lost)**로 정리하고 저장된 원래 실내등 값을 복구한다. Ended 화면을 3초 표시한 뒤 Idle로 간다.
  - 앱 재시작으로 Idle인 상태에서 `none`을 받아도 Prefs에 남은 실내등 원래 값이 있으면 manager가 복구한다.
  - Idle에서 `requested`를 받으면 요청 화면 상태를 복원한다. 새 `pickup.requested` 때의 헤드업 알림·TTS·STT 효과는 다시 실행하지 않는다.
  - Idle에서 `sharing`을 받으면 Sharing과 위치 전송을 시작한다. Requested에서 받으면 요청 알림도 닫는다.
  - 이미 Sharing·Nearby이면 `sharing` 메시지만으로 상태를 재설정하지 않는다. Idle에서 복원한 Sharing은 이후 위치·기어 이벤트로 Nearby 여부를 판단한다.
- 서버 재시작 후에는 세션을 복구하지 않으므로 같은 토큰으로 재연결해도 `none`이다. 폰 앱의 상태 동기화는 미구현 설계다.

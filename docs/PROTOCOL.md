# 마중 통신 규격

차 앱, 폰 앱, 서버가 공유하는 계약이다. 셋 중 하나를 바꾸면 이 문서를 먼저 고치고 나머지를 맞춘다.

- 전송: HTTPS(REST) + WebSocket(텍스트 프레임, JSON). STOMP는 쓰지 않는다.
- 인코딩: UTF-8 JSON. 필드 이름은 camelCase.
- 시각: Unix epoch 밀리초(`Long`).
- 좌표: WGS84 위경도(`Double`), 정확도는 미터(`Float`).
- 기본 URL: 앱 빌드 설정 `BuildConfig.SERVER_URL`. 로컬은 `http://10.0.2.2:8080`(에뮬레이터에서 호스트 PC), 배포는 Railway URL.

## 1. 신원과 토큰

로그인은 없다. 차와 탑승자는 서버가 서명한 토큰으로 구분한다.

| 토큰 | 발급 | 페이로드 | 보관 |
|---|---|---|---|
| vehicleToken | `POST /api/vehicles` | `{"k":"v","vid":"v_…"}` | 차 앱 SharedPreferences |
| riderToken | `POST /api/pairings` | `{"k":"r","vid":"v_…","rid":"r_…"}` | 폰 앱 SharedPreferences |

- 형식: `base64url(payloadJson) + "." + base64url(HMAC-SHA256(secret, payloadJson))`. 비밀키는 서버 설정 `maejung.token-secret`(환경변수 `MAEJUNG_TOKEN_SECRET`).
- 토큰에 이름 같은 개인정보를 넣지 않는다(WebSocket 주소의 쿼리로 가서 프록시 로그에 남을 수 있다). 탑승자 이름은 폰이 `pickup.request`에 실어 보낸다.
- 서버는 토큰을 저장하지 않고 서명만 검증한다. 그래서 **서버가 재시작돼도 페어링은 유지된다.** 진행 중인 세션은 메모리에만 있어서 재시작하면 사라진다(5절).
- 페어링 해제·토큰 폐기는 1차 범위가 아니다.

## 2. REST API

모든 응답은 JSON. 오류는 HTTP 상태 코드 + `{"code": "...", "message": "..."}`.

공통 오류:

| 상태 | code | 조건 |
|---|---|---|
| 401 | `INVALID_TOKEN` | `Authorization: Bearer` 헤더가 없거나, 서명이 틀리거나, 토큰 종류가 다름(차 API에 riderToken) |
| 400 | `BAD_REQUEST` | 본문 JSON 오류, 필수 필드 누락 |

### `POST /api/vehicles` — 차 등록

차 앱 첫 실행 때 한 번 호출한다. 요청 본문 없음.

응답 `201`:
```json
{ "vehicleId": "v_7f3a1b2c4d5e", "vehicleToken": "…" }
```

### `POST /api/pairing-codes` — 연결 코드 발급

차 화면의 "가족 연결" 버튼. 헤더 `Authorization: Bearer <vehicleToken>`.

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

오류: `404 PAIRING_CODE_NOT_FOUND`(없거나 만료), `400 INVALID_RIDER_NAME`(공백 제외 1~20자 아님)

- 차가 WebSocket에 붙어 있으면 `pairing.completed`를 보낸다(3.3).
- 폰은 `riderName`을 SharedPreferences에 저장해 두고 `pickup.request`마다 보낸다.

### `GET /health`

`200 {"status":"ok"}`. Railway 헬스체크.

## 3. WebSocket

- 주소: `wss://<host>/ws?token=<vehicleToken 또는 riderToken>` (로컬은 `ws://`).
- 서버는 핸드셰이크를 그대로 받아들이고, `afterConnectionEstablished`에서 토큰을 검증한다. 틀리면 **close code 4401**로 닫는다(HandshakeInterceptor에서 거절하면 클라이언트가 HTTP 응답만 받아 구분이 어렵기 때문).
- 한 토큰 주인(principal)당 연결 하나. 새 연결이 오면 이전 연결을 **4409**로 닫는다.
- 클라이언트 재연결 규칙:
  - OkHttp `pingInterval(15s)`.
  - 일반 끊김·`onFailure`: 1, 2, 4, 8, 최대 15초 간격으로 다시 붙는다.
  - **4401을 받으면 재연결을 멈춘다.** 차는 토큰을 지우고 다시 등록(`POST /api/vehicles`)한다. 기존 가족 연결은 다시 해야 한다. 폰은 토큰을 지우고 연결 화면으로 간다.
  - 4401은 사실상 서버 비밀키가 바뀌었을 때만 생긴다. 이때 차는 새 vehicleId를 받지만, 폰도 같이 4401을 받아 연결 화면으로 가므로 다시 연결하면 된다. 비밀키를 바꾸지 않았는데 폰이 계속 `VEHICLE_OFFLINE`만 받는다면 차가 다른 vehicleId로 재등록된 것이니 가족 연결을 다시 한다.
  - **4409를 받으면 재연결하지 않는다**(같은 기기에서 새 연결이 이미 붙어 있다).
- 메시지 공통 형식:

```json
{ "type": "pickup.request", "sessionId": "s_…", "ts": 1762412400000, "payload": { } }
```

- **서버 → 클라이언트** 메시지 중 세션과 관련된 것에는 항상 `sessionId`가 들어 있다.
- **클라이언트 → 서버** 메시지의 `sessionId`는 넣지 않아도 된다. 서버는 보낸 사람(vehicleId 또는 riderId)의 진행 중 세션을 인덱스로 찾고, 클라이언트가 보낸 `sessionId`는 무시한다.
- `payload`가 없는 메시지는 `{}`를 보낸다. `ts`는 보낸 쪽 시각이며 참고용이다.

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
| `eta.update` | `{ "seconds", "meters", "source": "navi" \| "estimate" }` | 30초마다 또는 값이 20% 넘게 바뀔 때. SHARING에서만 처리 |
| `vehicle.light` | `{ "on": true }` | 실내등을 켰거나 껐다(복구 포함). SHARING에서만 처리 |
| `session.boarded` | `{}` | 차가 탑승을 판정했다(DESIGN.md 3절). SHARING에서만 |
| `session.end` | `{ "reason": "manual" }` | 차에서 수동 종료 |

**위치 heartbeat**: 양쪽 클라이언트는 SHARING 동안 **3초마다 또는 10m 이동마다, 그리고 움직이지 않아도 최소 10초마다** 마지막 위치를 `location.update`로 보낸다. 정차 중인 차가 `peer_lost`로 끊기지 않게 하기 위해서다. 위치를 아직 한 번도 못 얻었으면 보내지 않는다.

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
| `peer.eta` | `{ "seconds", "meters", "source" }` | 차가 보낸 `eta.update` 그대로 |
| `peer.light` | `{ "on" }` | 차가 보낸 `vehicle.light` 그대로 |
| `session.ended` | `{ "reason" }` | 세션 종료. 거절도 이걸로 온다(`reason: "rejected"`) |
| `error` | `{ "code", "message" }` | |

### 3.5 WebSocket 오류 코드 (`error` 메시지)

| code | 의미 | 받는 쪽 처리 |
|---|---|---|
| `SESSION_ALREADY_ACTIVE` | 이 탑승자 또는 이 차에 진행 중 세션이 있다 | 폰: "이미 다른 요청이 진행 중이에요" 후 Idle |
| `VEHICLE_OFFLINE` | 차가 연결돼 있지 않아 요청을 전달하지 못했다. 세션은 만들지 않는다 | 폰: "차가 꺼져 있어요" 후 Idle |
| `SESSION_NOT_FOUND` | 진행 중 세션이 없는데 세션 메시지를 보냈다 | 로그만 |
| `INVALID_STATE` | 지금 상태에서 할 수 없는 메시지(예: REQUESTED에서 `session.boarded`) | 로그만 |
| `BAD_MESSAGE` | JSON 파싱 실패, 모르는 type, 필드 누락 | 로그만 |

## 4. 세션 수명 (서버 기준)

서버 상태는 REQUESTED, SHARING, ENDED 세 가지다. 차 앱의 Nearby는 서버가 모른다.

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
| `request_timeout` | REQUESTED가 된 지(createdAt) 5분 |
| `peer_lost` | SHARING에서 어느 한쪽의 마지막 위치 수신 시각이 2분 넘게 지남. 위치를 한 번도 안 보낸 쪽은 accept 시각을 기준으로 센다 |
| `max_duration` | createdAt부터 60분 |

- 종료되면 서버는 세션과 마지막 위치를 메모리에서 지우고, 연결돼 있는 양쪽에 `session.ended`를 보낸다. 거절도 양쪽에 `session.ended(rejected)` 하나만 보낸다.
- 차 하나에 진행 중 세션은 하나만. 같은 차의 다른 탑승자가 요청하면 그 폰에 `error SESSION_ALREADY_ACTIVE`.
- 서버는 위치를 디스크나 로그에 남기지 않는다. 로그에는 type, sessionId, principal 종류(v/r)만 찍는다.

## 5. 연결 직후 상태 동기화 (`session.state`)

재연결, 앱 재시작, 서버 재시작 뒤에 양쪽 상태를 맞추기 위해, 서버는 WebSocket 연결(토큰 검증 통과) 직후 항상 `session.state`를 보낸다.

```json
{ "type": "session.state", "sessionId": "s_…", "ts": 0,
  "payload": { "state": "none" | "requested" | "sharing", "riderId": "r_…", "riderName": "민지" } }
```

- `state: "none"`이면 `sessionId`, `riderId`, `riderName`이 없다.
- 클라이언트 처리:
  - 로컬은 진행 중(Requested·Sharing·Nearby, 폰은 Requesting·Sharing)인데 `none`이 오면 → **Ended(peer_lost)**로 처리한다. 차는 실내등을 복구한다.
  - 차가 `requested`를 받으면 요청 화면을 다시 띄운다(음성은 다시 하지 않는다).
  - `sharing`을 받으면 Sharing으로 맞춘다. 차의 Nearby 여부는 차가 다음 위치·기어 이벤트로 다시 판단한다.

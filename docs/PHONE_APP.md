# 마중 폰 앱 설계 (탑승자용)

탑승자(학원생 등)가 쓰는 Android 앱. 저장소의 `phone/` 모듈이다. 메시지는 [PROTOCOL.md](PROTOCOL.md)가 기준이다. 빌드 공통 규칙(플러그인, JVM 17, Compose BOM)은 [CAR_APP.md](CAR_APP.md) 1절을 따른다.

## 1. 범위

- **1차(11/15)는 Android만.** iOS는 만들지 않는다.
- 화면 3개: 연결, 홈(요청), 마중 중. 그 밖에 요청 대기·종료 문구는 홈 화면 안의 상태 표시로 처리한다.
- 지도는 1차에서 넣지 않는다(Google Maps API 키 발급·과금 관리를 피한다). 거리, 방향 화살표, ETA, 실내등 안내를 큰 글씨로 보여준다.
- 차 이름은 1차에서 "엄마 차"로 고정한다(연결할 때 입력받는 건 PT 단계).

## 2. 모듈과 빌드

- `phone/` 모듈. namespace·applicationId `com.maejung.phone`. minSdk 26, **compileSdk 35**, targetSdk 34.
- 일반 휴대폰 앱이다. `android.hardware.type.automotive`를 선언하지 않는다.
- 의존성: Compose(Material3, 차 앱과 같은 BOM), `project(":protocol")`, OkHttp 4.12.0, `com.google.android.gms:play-services-location`(작업할 때 compileSdk 35와 호환되는 최신 안정 버전을 고정하고 여기 적는다).
- 서버 주소 `BuildConfig.SERVER_URL`. 차 앱과 같은 규칙(debug `http://10.0.2.2:8080`, release는 Gradle 속성 `maejung.serverUrl`).

## 3. 패키지 구조

```
phone/src/main/java/com/maejung/phone/
├── MaejungPhoneApp.kt            Application. PhoneSessionManager를 만들어 들고 있는다
├── MainActivity.kt
├── session/
│   ├── PhoneState.kt             Unpaired / Idle(message?) / Requesting(sessionId?) / Sharing(sessionId, 화면 데이터) / Ended(reason)
│   └── PhoneSessionManager.kt    Application 범위. WebSocket 연결, 상태(StateFlow), 위치 전송을 소유
├── net/                          WebSocketTransport, ApiClient (차 앱과 같은 방식, 재연결 규칙은 PROTOCOL.md 3절)
├── location/
│   ├── FusedLocationSource.kt    FusedLocationProviderClient, 3초 간격, PRIORITY_HIGH_ACCURACY
│   └── LocationShareService.kt   포그라운드 서비스(foregroundServiceType="location"). Sharing 동안만 실행
├── store/Prefs.kt                riderToken, riderName, vehicleId
└── ui/
    ├── PairScreen.kt             이름 + 6자리 코드 입력 → POST /api/pairings
    ├── HomeScreen.kt             큰 "데리러 와줘" 버튼. Requesting·Ended·오류 문구도 여기서 표시
    ├── SharingScreen.kt          "엄마 차까지 120m", 방향 화살표, ETA, 실내등 안내, 종료 버튼
    └── PhoneViewModel.kt         PhoneSessionManager.state를 구독만 한다
```

- **WebSocket 연결과 상태는 Application 범위의 `PhoneSessionManager`가 소유한다.** ViewModel(Activity 범위)에 두면 화면이 꺼지거나 태스크가 제거될 때 연결이 사라진다.
- `LocationShareService`는 위치를 받아 `PhoneSessionManager`를 통해 보낸다. 서비스가 프로세스를 살려 두는 역할도 한다.

## 4. 상태 전이

| 현재 | 이벤트 | 다음 | 동작 |
|---|---|---|---|
| Unpaired | 연결 성공(`POST /api/pairings` 201) | Idle | 토큰·이름 저장, WebSocket 연결 |
| Unpaired | 404·400 | Unpaired | "코드를 다시 확인해 주세요" |
| Idle | "데리러 와줘" 버튼 | Requesting(null) | 위치 퍼미션 확인(없으면 요청, 거부 시 Idle에 안내) 후 `pickup.request {riderName}`. **WebSocket이 연결되지 않은 동안 버튼은 비활성**("연결 중…") |
| Requesting(null) | 10초 안에 `pickup.pending`·`error`가 없음 | Idle("연결을 확인해 주세요") | 요청이 유실된 경우 |
| Requesting | `pickup.pending` | Requesting(sessionId) | "엄마 차에 알렸어요" |
| Requesting | `error VEHICLE_OFFLINE` | Idle("차가 꺼져 있어요") | |
| Requesting | `error SESSION_ALREADY_ACTIVE` | Idle("이미 다른 요청이 진행 중이에요") | |
| Requesting | 취소 버튼 | Ended(manual) | `session.end manual` |
| Requesting | `pickup.accepted` | Sharing | LocationShareService 시작 |
| Sharing | `peer.location`, `peer.eta`, `peer.light` | Sharing | 화면 갱신. `peer.light on`이면 "실내등 켜진 차가 엄마 차예요"를 크게 강조 |
| Sharing | 종료 버튼(화면 또는 알림) | Ended(manual) | `session.end manual`, 서비스 중지 |
| Requesting·Sharing | `session.ended(reason)` | Ended(reason) | 서비스 중지 |
| Requesting·Sharing | `session.state none` | Ended(peer_lost) | 서비스 중지 |
| Idle | `session.state requested` | Requesting(sessionId) | |
| Idle·Requesting | `session.state sharing` | Sharing | 서비스 시작 |
| Ended | 3초 뒤 | Idle | |
| 아무 상태 | WebSocket close 4401 | Unpaired | 토큰 삭제 |

종료 문구:

| reason | 문구 |
|---|---|
| `boarded` | "탑승 확인! 위치공유를 끝냈어요" |
| `rejected` | "엄마가 지금은 못 온대요" |
| `manual` | "위치공유를 끝냈어요" |
| `request_timeout` | "응답이 없어요. 다시 요청해 볼까요?" |
| `peer_lost` | "연결이 끊겨서 위치공유를 끝냈어요" |
| `max_duration` | "1시간이 지나 위치공유를 끝냈어요" |

## 5. 위치와 앱 종료

- **위치 전송은 Sharing일 때만** 한다. 규칙은 PROTOCOL.md 3.2 heartbeat(3초 또는 10m, 최소 10초마다).
- 포그라운드 서비스 알림: "엄마 차와 위치 공유 중" + "끝내기" 액션.
- **앱을 완전히 닫으면**(최근 앱에서 스와이프) 서비스의 `onTaskRemoved`에서 `PhoneSessionManager`로 `session.end(app_closed)`를 보내고 서비스를 멈춘다. 못 보내더라도 서버가 2분 뒤 `peer_lost`로 끝낸다.
- 퍼미션: `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`(33 이상). 위치 퍼미션은 "데리러 와줘"를 처음 누를 때 요청한다. 백그라운드 위치 퍼미션은 필요 없다(포그라운드 서비스이므로).

## 6. 에뮬레이터 검증

- **Google APIs(Play 서비스) 이미지의 폰 AVD가 필요하다.** 아직 만들지 않았다(TASKS.md P1).
- 폰 위치 주입: `adb -s emulator-5556 emu geo fix <경도> <위도>`. 차 에뮬레이터가 보통 5554, 폰이 5556.
- 두 에뮬레이터 모두 `10.0.2.2:8080`으로 호스트 PC의 로컬 서버에 붙는다.

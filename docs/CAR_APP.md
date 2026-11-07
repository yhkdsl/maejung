# 마중 차 앱 설계 (Pleos / Android Automotive)

차 앱은 저장소의 `app/` 모듈이다. 탑승 판정·실내등 규칙의 근거는 [DESIGN.md](DESIGN.md) 2~4절, 메시지는 [PROTOCOL.md](PROTOCOL.md)가 기준이다. Vehicle SDK 시그니처·퍼미션은 DESIGN.md 5절에 있다. **차 앱 상태 머신의 기준 표는 이 문서 3절이다.**

## 1. 모듈과 빌드

| 모듈 | 내용 | 비고 |
|---|---|---|
| `app/` | 차 앱. namespace·applicationId `com.maejung` | AAOS |
| `phone/` | 폰 앱. [PHONE_APP.md](PHONE_APP.md) | 추가 예정 |
| `protocol/` | 메시지 data class와 JSON 직렬화. `app`과 `phone`이 같이 쓴다 | 순수 Kotlin(JVM) 모듈, Android 의존성 없음 |
| `server/` | 서버. [SERVER.md](SERVER.md) | **별도 Gradle 프로젝트**, 루트 `settings.gradle.kts`에 넣지 않는다 |

빌드 규칙:

- 빌드 JDK는 Android Studio JBR 21. AGP 8.10.1, Kotlin 2.1.21, Gradle 8.14.
- **compileSdk 35, targetSdk 34.** 최근 AndroidX(Compose 1.8 이상, activity-compose 1.10 이상, core 1.15 이상)는 compileSdk 35 이상을 요구해서 34로는 `checkDebugAarMetadata`에서 실패한다. android-35 플랫폼은 설치되어 있다. 에뮬레이터(API 34)에서 그대로 실행된다. 차 앱 minSdk 28.
- 루트 `build.gradle.kts`에 플러그인을 `apply false`로 모은다:
  ```kotlin
  id("com.android.application") version "8.10.1" apply false
  id("org.jetbrains.kotlin.android") version "2.1.21" apply false
  id("org.jetbrains.kotlin.jvm") version "2.1.21" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
  id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21" apply false
  ```
- 모든 모듈은 JVM 17 바이트코드로 맞춘다. Android 모듈은 `compileOptions` 17 + `kotlinOptions.jvmTarget = "17"`(현재 `app`과 같음), `protocol`은 `java { source/targetCompatibility = 17 }` + `kotlin { compilerOptions { jvmTarget = JVM_17 } }`. 빌드 JDK(JBR 21)에 17 툴체인이 없어서 `jvmToolchain(17)`은 쓰지 않는다.
- UI는 Jetpack Compose(Material3). 고정한 버전(11/07): Compose BOM `2025.05.01`(ui 1.8.2, material3 1.3.2), activity-compose `1.10.1`, lifecycle `2.9.0`, core-ktx `1.16.0`, kotlinx-coroutines-android `1.10.2`. 더 최신(2026년) 버전은 compileSdk 36·새 Kotlin을 요구할 수 있어 올리지 않았다.
- JSON은 `kotlinx-serialization-json` 1.8.1(`protocol` 모듈이 `api`로 노출). **메시지 인코딩·디코딩은 `com.maejung.protocol.Protocol`만 쓴다**(11/07 구현 완료):
  - `Protocol.encode(message, ts, sessionId?)` → 봉투 JSON 문자열. `Protocol.decode(text)` → `Received(message, sessionId, ts)`, JSON·봉투 형식이 틀리면 null, 모르는 type은 `Unknown(type, raw)`.
  - 메시지는 `ClientMessage`(보내는 것)·`ServerMessage`(받는 것) sealed 계층의 data class·data object다(`Messages.kt`). 값 상수는 `SessionStates`, `EndReasons`, `EtaSources`, `ErrorCodes`, `CloseCodes`.
  - REST 본문은 `Rest.kt`(`VehicleRegistered`, `PairingCodeIssued`, `PairingRequest`, `PairingResult`, `ApiError`, `ApiPaths`)를 `Protocol.json`으로 읽고 쓴다.
  - 다형 직렬화의 `classDiscriminator`는 모르는 type에서 예외를 던져서 쓰지 않고, type을 먼저 읽어 나눈다.
- 네트워크는 OkHttp 4.12.0(이미 추가됨, WebSocket 확인 완료).
- 서버 주소는 `BuildConfig.SERVER_URL`(`buildFeatures { buildConfig = true }`).
  - debug: `http://10.0.2.2:8080`. `res/xml/network_security_config.xml`에서 `10.0.2.2`만 평문 허용.
  - release: Gradle 속성 `maejung.serverUrl`(`local.properties` 또는 `-P`)에서 읽는다. 값이 없으면 release 빌드를 실패시킨다. 값은 S5(Railway 배포) 후 사용자에게 받는다.

## 2. 패키지 구조

```
app/src/main/java/com/maejung/
├── MaejungApp.kt                 Application. CarSessionManager를 만들어 들고 있는다(DI 프레임워크 없음)
├── MainActivity.kt               Compose 진입점, 런처, distractionOptimized
├── session/
│   ├── CarSessionMachine.kt      순수 Kotlin 상태 머신. `reduce(CarSession, CarEvent)` → (새 CarSession, 효과 목록). 11/07 구현, 테스트 38개
│   ├── CarSession.kt             `Phase`(Idle / Requested / Sharing / Nearby / Ended(reason)) + 컨텍스트를 담은 `CarSession`, `CarEvent`(3.2), `CarEffect`(3.3)
│   ├── Korean.kt                 이름 조사(민지가/준영이가), 음성 답 해석 `VoiceAnswers.parse`(승인·거절·무시)
│   └── CarSessionManager.kt      Application 범위. 머신 + transport + vehicle + location + voice를 묶어 이벤트를 넣고 효과를 실행. state를 StateFlow로 공개
├── vehicle/
│   ├── VehicleSignals.kt         인터페이스: gear: Flow<Gear>, doors: Flow<DoorPosition>, readCabinLight(), setCabinLight(CabinSwitch)
│   ├── PleosVehicleSignals.kt    Vehicle SDK 구현(스파이크 코드 참고)
│   └── FakeVehicleSignals.kt     테스트·데모용
├── net/
│   ├── SessionTransport.kt       인터페이스: connect(token), send(msg), incoming: Flow<ServerMessage>, connection: Flow<연결 상태>
│   ├── WebSocketTransport.kt     OkHttp 구현. 재연결 규칙은 PROTOCOL.md 3절(4401·4409는 재연결 안 함)
│   ├── FakeSessionTransport.kt   서버 없이 요청·위치·종료를 흉내 낸다(A4). `-Pmaejung.fakeServer=true` 빌드에서만 쓴다(`BuildConfig.FAKE_SERVER`)
│   └── ApiClient.kt              REST: 차 등록, 연결 코드 발급. 서버가 꺼져 있으면 Manager가 5초마다 등록을 다시 시도한다
├── location/
│   ├── LocationSource.kt         인터페이스: locations: Flow<Fix>
│   ├── AndroidLocationSource.kt  LocationManager(GPS_PROVIDER), minTime 3초, minDistance 0
│   └── (FixedLocationSource.kt)  C1에서 에뮬레이터 위치 주입이 돼서 만들지 않았다
├── voice/
│   ├── VoiceIO.kt                인터페이스: speak(text), listen(): String?
│   └── PleosVoiceIO.kt           말하기: Pleos TextToSpeech SDK `Mode.ON_DEVICE`, 초기화 전 요청은 모아 두었다가 읽는다. 듣기(listen)는 아직 null(A6·A8에서 SpeechRecognizer)
├── eta/
│   └── EtaCalculator.kt          길안내 중이면 마지막 `onDrivingInfo` 값(`source: navi`), 아니면 직선거리 × 1.3 ÷ 5.6m/s(20km/h, `source: estimate`)
├── navi/
│   └── PickupNavigator.kt        NaviHelper로 탑승자 위치까지 길안내 시작(`requestRoute`)·취소(`cancelRoute`), `onDrivingInfo`를 EtaCalculator에 전달
├── alert/
│   ├── PickupNotifications.kt    요청 알림(수락·거절 버튼, 헤드업), 상태 알림(버튼 없음: "가까이 왔어요", "탑승 확인")
│   └── PickupAlertReceiver.kt    알림 버튼 → CarSessionManager에 UserAccept·UserReject
├── service/
│   └── CarSessionService.kt      포그라운드 서비스. 세션 진행 중(Requested~Nearby) 앱이 화면 뒤에 있어도 위치 전송·탑승 감지가 멈추지 않게 한다
├── store/
│   └── Prefs.kt                  vehicleId, vehicleToken, 연결된 가족 목록(riderId, riderName)
└── ui/
    ├── HomeScreen.kt             연결 상태, 연결된 가족 목록, "가족 연결" 버튼(코드와 남은 시간 표시)
    ├── RequestScreen.kt          마중 앱이 앞에 있을 때의 요청 화면: 이름, 승인·거절 큰 버튼(다른 앱이 앞에 있으면 헤드업 알림이 대신한다)
    ├── SharingScreen.kt          거리, 방향 화살표, ETA, 실내등 상태, 종료 버튼 (Sharing·Nearby 공용)
    ├── EndedScreen.kt            종료 이유 문구 3초
    └── CarViewModel.kt           CarSessionManager.state를 구독만 한다
```

- **운전자는 마중 앱 화면을 보지 않아도 된다.** 요청은 지금 앞에 있는 앱(내비, 음악 등) 위에 헤드업 알림으로 뜨고, 같은 내용을 음성으로도 묻는다. 수락 후 진행 안내도 버튼 없는 알림과 음성으로만 한다(2026-11-07 결정).
- 헤드업 알림 규칙(에뮬레이터 확인, DESIGN.md 7절):
  - 채널 중요도 `IMPORTANCE_HIGH`, 카테고리 `CATEGORY_MESSAGE`. `CATEGORY_CALL`로 하면 AAOS가 전화 수신처럼 초록·빨강 아이콘 버튼으로 바꿔 그려서 "수락/거절" 글자가 사라진다.
  - 헤드업은 6~8초 뒤 알림 센터로 들어간다. 그래서 요청은 알림과 동시에 음성으로 묻고 음성 답을 기다린다. 알림 센터에 남은 알림의 버튼도 계속 동작한다.
  - 새 요청을 띄우기 전에 같은 id의 알림을 `cancel`한다. 알림 센터에 이전 알림이 남아 있으면 갱신만 되고 헤드업으로 다시 뜨지 않는다.
  - 퍼미션 `POST_NOTIFICATIONS`(런타임, 33 이상).
- 연결과 세션은 **Application 범위의 `CarSessionManager`가 소유**한다. 화면 회전·Activity 재생성에 끊기지 않게 하기 위해서다. ViewModel은 구독과 사용자 입력 전달만 한다.
- `spike/VehicleSpikeActivity.kt`는 검증 기록으로 남긴다. 런처는 `MainActivity`로 옮겼다. 스파이크는 런처에서 빼되 adb로 열 수 있게 `exported=true`로 둔다(`adb shell am start -n com.maejung/.spike.VehicleSpikeActivity`). `exported=false`면 셸에서도 열 수 없다.
- `pairing.completed`는 머신을 거치지 않는다. Manager가 Prefs의 가족 목록에 추가하고 HomeScreen에 "{이름} 연결됨"을 3초 보여준다.

## 3. 상태 머신

`CarSessionMachine`은 Android 의존성 없이 단위 테스트할 수 있어야 한다. 거리 계산(`Location.distanceBetween`)은 머신 밖(Manager)에서 하고 결과를 이벤트로 넣는다.

### 3.1 상태와 컨텍스트

| 상태 | 뜻 |
|---|---|
| Idle | 진행 중 세션 없음 |
| Requested(sessionId, riderName) | 요청 받음, 승인 대기 |
| Sharing(sessionId, riderName) | 위치 공유 중 |
| Nearby(sessionId, riderName, openedDoors) | 탑승자 50m 이내 + 기어 P, 실내등 켠 상태 |
| Ended(reason) | 끝남. 3초 뒤 Idle. reason은 PROTOCOL.md 4절 값 그대로 |

상태와 별개로 머신이 늘 들고 있는 컨텍스트:

- `gear`: 마지막 기어 값
- `distanceM`: 마지막 거리(둘 중 한쪽 위치가 없으면 null)
- `lightInfo`: 마지막 `CabinLightInfo(current: CabinSwitch?, supportsOn: Boolean)`
- `lightBefore`: Nearby에 들어가며 저장한 실내등 원래 값(`CabinSwitch?`). null이면 "켠 적 없음"

`CabinSwitch`는 앱 안의 enum(`OFF, ON, DAYTIME_RUNNING, AUTOMATIC`)으로, SDK `LightSwitch`와 1:1로 바꾼다. Boolean으로 줄이면 원래 AUTOMATIC이었던 값을 복구할 수 없어서 enum 그대로 저장한다. 머신이 SDK 타입에 의존하지 않게 하려고 사본을 둔다.

### 3.2 이벤트 (`CarEvent`)

| 이벤트 | 출처 |
|---|---|
| `Server(message)` | `session.state`, `pickup.requested`, `session.ended`, `peer.location`(Manager가 거리 계산 후 `Distance`로도 넣음) |
| `Gear(value)` | VehicleSignals |
| `Door(area, position)` | VehicleSignals, 승객 문 `ROW_1_RIGHT`·`ROW_2_LEFT`·`ROW_2_RIGHT`만 |
| `CabinLight(info)` | Manager가 `checkCabinLightSwitchCapabilities` + `getCabinLightSwitch`로 읽어 넣는다. 시점: 앱 시작, 그리고 Sharing에 들어갈 때마다(StartLocationSharing 실행 시). 실내등 "스위치" 구독 API는 문서에 없다(`registerCabinLightState`는 실제 점등 상태 `LightState`용이라 저장·복구에 쓰지 않는다) |
| `Distance(meters?)` | Manager. 차·탑승자 위치가 바뀔 때마다 |
| `UserAccept`, `UserReject`, `UserEnd` | 버튼 또는 음성 |
| `EndedTimeout` | Ended 진입 3초 뒤 Manager가 넣음 |

### 3.3 효과 (`CarEffect`)

`Send(message)`, `SetCabinLight(value: CabinSwitch)`, `SaveLightBefore(value: CabinSwitch?)`, `Speak(text)`, `Listen`, `StartLocationSharing`, `StopLocationSharing`, `ShowRequestAlert(riderName)`, `DismissRequestAlert`, `Notify(text)`(버튼 없는 헤드업), `StartNavigation(lat, lng, riderName)`, `StopNavigation`.

- 머신 컨텍스트에 `peer`(탑승자 마지막 좌표)와 `navStarted: Boolean`을 더 둔다. `peer.location` 이벤트가 올 때마다 갱신한다.
- **길안내 규칙**: Sharing에서 처음으로 거리가 계산됐을 때 **500m 이상이면** `StartNavigation(peer)` 한 번, `navStarted = true`. 500m 미만(학원 앞 대기)이면 길안내하지 않는다. 세션 중에는 다시 걸지 않는다(아이가 걸어서 움직이는 정도는 무시). 종료로 가는 모든 전이에서 `navStarted`면 `StopNavigation`.

- 실내등 저장·복구 판단은 **머신이 한다.** 효과 실행기는 `SetCabinLight`를 그대로 SDK 호출로 바꿀 뿐이다.
- 복구는 두 가지다.
  - **복구(공유 유지)**: Nearby → Sharing으로 돌아갈 때. `lightBefore`가 null이 아니면 `SetCabinLight(lightBefore)` + `Send(vehicle.light {on: lightBefore == ON})` + `SaveLightBefore(null)`, `lightBefore = null`.
  - **복구(종료)**: Ended로 갈 때. `SetCabinLight(lightBefore)` + `SaveLightBefore(null)`만 내고 **Send는 하지 않는다.** 서버는 SHARING에서만 `vehicle.light`를 받기 때문이다.
- `SaveLightBefore`는 Manager가 Prefs에 저장한다. 차 앱이 Nearby 도중 죽었다 살아나도 원래 값을 잃지 않게 하기 위해서다. 앱 시작 시 Prefs에 값이 있고 `session.state`가 `none`이면 Manager가 바로 그 값으로 복구하고 지운다. `sharing`이면 그 값을 머신의 `lightBefore` 초기값으로 넣는다.

### 3.4 전이 표

| 현재 | 이벤트 | 다음 | 효과 |
|---|---|---|---|
| Idle | `pickup.requested` | Requested | ShowRequestAlert, Speak("{이름}가 데리러 와 달래요. 갈까요?"), Listen |
| Requested | UserAccept(알림·화면 버튼, 또는 음성 "응/그래/갈게/좋아" 포함) | Sharing | DismissRequestAlert, Send(`pickup.accept`), StartLocationSharing |
| Requested | UserReject(알림·화면 버튼, 또는 음성 "아니/안 돼/싫어" 포함) | Ended(rejected) | DismissRequestAlert, Send(`pickup.reject`) |
| Sharing | 이 세션에서 처음 null이 아닌 Distance(`navDecided == false`) | Sharing | `navDecided = true`. `distance ≥ 500`이고 탑승자 좌표가 있으면 StartNavigation(peer), `navStarted = true`, Speak("{이름}에게 가는 길을 안내할게요"). 500m 미만이면 이 세션에서는 다시 판단하지 않는다 |
| Sharing | 조건 `distance ≤ 50 && gear == P` 충족(Distance 또는 Gear 이벤트) | Nearby | `lightInfo.supportsOn`이면: `lightBefore == null`일 때만 `lightBefore = lightInfo.current ?: OFF`, SaveLightBefore(lightBefore). 그다음 SetCabinLight(ON), Send(`vehicle.light on`). 공통: Notify("{이름}가 가까이 왔어요"), Speak(같은 문장) |
| Nearby | `distance > 70 \|\| distance == null \|\| gear != P` | Sharing | 복구(공유 유지) |
| Nearby | Door(승객 문, 1) | Nearby | openedDoors에 추가 |
| Nearby | Door(openedDoors에 있는 문, 0) 그리고 `gear == P && distance ≤ 30` | Ended(boarded) | Send(`session.boarded`), 종료 공통, Notify·Speak("{이름} 탑승 확인, 위치공유를 끝낼게요") |
| Requested·Sharing·Nearby | UserEnd | Ended(manual) | Send(`session.end manual`), 종료 공통 |
| Requested·Sharing·Nearby | `session.ended(reason)` | Ended(reason) | 종료 공통 |
| Requested·Sharing·Nearby | `session.state none` | Ended(peer_lost) | 종료 공통 |
| Idle | `session.state requested` | Requested | (음성 없음) |
| Idle·Requested | `session.state sharing` | Sharing | (Requested였으면 DismissRequestAlert), StartLocationSharing |
| Ended | EndedTimeout | Idle | - |
| Ended | `pickup.requested` | Requested | 위 Idle 행과 같음 |

- **종료 공통** = DismissRequestAlert(Requested에서 끝날 때), 복구(종료), StopLocationSharing, `navStarted`면 StopNavigation.
- 표에 없는 조합은 상태를 바꾸지 않고 효과도 없다(예: Idle에서 Door, Sharing에서 UserAccept).
- Nearby에 들어오기 전에 열린 문은 세지 않는다. Nearby를 나가면 openedDoors를 비운다.
- `lightInfo.supportsOn`이 false면 실내등 효과와 `vehicle.light`를 모두 생략한다. 나머지 흐름은 같다.
- 실내등이 원래 켜져 있었으면(`lightBefore == ON`) 복구해도 켜진 채로 둔다. AUTOMATIC이었으면 AUTOMATIC으로 되돌린다.
- `lightBefore`가 이미 있으면(앱 재시작 후 Prefs에서 복원) Nearby에 다시 들어가도 덮어쓰지 않는다.
- 음성 인식이 실패하거나 지원되지 않으면 버튼만으로 동작해야 한다. 음성 결과는 `VoiceAnswers.parse`로 해석하고(거절 단어를 먼저 본다, "안 갈게" 때문), 승인·거절 단어가 다 없으면 무시한다. "어"는 "어디야"에도 들어 있어 승인 단어에서 뺐다.
- 안내 문장의 이름 조사는 받침에 따라 고른다(민지가 / 준영이가, 민지에게 / 준영이에게).
- 새 요청(Requested 진입) 때 탑승자 좌표·거리·길안내 판단을 비운다. 실내등 원래 값(`lightBefore`)은 비우지 않는다(앱 재시작 복원값일 수 있다).
- 효과 순서는 테스트로 고정되어 있다. 종료 전이는 `[서버에 보낼 메시지] → DismissRequestAlert → 실내등 복구 → StopLocationSharing → StopNavigation → [안내]` 순이다.
- 위치 전송(StartLocationSharing)은 PROTOCOL.md 3.2의 heartbeat 규칙을 따른다(3초 또는 10m, 최소 10초마다).

## 4. 화면 원칙 (주행 중)

- **지도는 1차에서 넣지 않는다.** 차에는 GMS가 없어 Google Maps SDK를 못 쓴다. 거리(m), 방향 화살표(차 → 탑승자 방위각), ETA를 큰 글씨로 보여준다.
- 버튼은 화면 높이의 1/4 이상, 글자 32sp 이상. 화면당 버튼 최대 2개.
- 다크 배경 기본.

## 5. 퍼미션

- 매니페스트(현재 있는 것): CAR_DRIVE, CAR_CLIMATE, CAR_LIGHTS, CAR_WINDOWS, CAR_DOORS, CAR_SEATS, CONTROL_CAR_CLIMATE, CONTROL_CAR_WINDOWS, CONTROL_CAR_LIGHTS, INTERNET.
- **추가 필요**: `android.permission.ACCESS_FINE_LOCATION`, `RECORD_AUDIO`(STT). 둘 다 런타임 퍼미션이라 앱에서 요청한다. 에뮬레이터 검증 때는 `adb shell pm grant --user 10 com.maejung android.permission.ACCESS_FINE_LOCATION`으로 미리 줄 수 있다(**`--user 10` 필수**, 차 에뮬레이터 앱은 사용자 10에서 돈다).
- Pleos SDK 퍼미션: `pleos.car.permission.TTS_SERVICE`(음성 출력), `NAVI_ROUTE`(내비 정보 조회), `NAVI_ROUTE_SEARCH`(길안내 시작·취소).
- 알림·서비스: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`(서비스 `foregroundServiceType="location"`).
- 1차 앱 본체에 필요 없는 창문·공조 제어 퍼미션은 A1에서 정리한다(스파이크만 쓰던 것).

## 6. 에뮬레이터 검증

- 실행·설치·Virtual Console 사용법은 루트 `CLAUDE.md`.
- 기어: Virtual Console Gear Selection(P/R/N/D). 문: BODY 패널 FL/FR/RL/RR 클릭(열림↔닫힘). 브라우저 폭 1600px 이상에서 보인다.
- 차 위치 주입: `adb -s emulator-5554 emu geo fix <경도> <위도>`(경도가 먼저). **Pleos 에뮬레이터의 LocationManager에 실제로 들어오는지는 아직 확인 안 됨**(TASKS.md C1).
- 실내등은 Virtual Console에 상태 표시가 없다. 앱 로그의 `getCabinLightSwitch` 조회값으로 확인한다.
- 데모 좌표는 TASKS.md D1.

# 마중 차 앱 설계 (Pleos / Android Automotive)

차 앱은 저장소의 `app/` 모듈이다. **구현 기준: 2026-11-07, HEAD `74821e339a95ab99f4a233813f8919452a66d27a`.** 이 문서는 현재 소스 구현을 설명하며, 문서와 코드가 다르면 코드를 기준으로 갱신한다. 탑승 판정·실내등 규칙의 개요는 [DESIGN.md](DESIGN.md) 2~4절, 메시지 설명은 [PROTOCOL.md](PROTOCOL.md)에 있다. 상태 전이는 `session/CarSessionMachine.kt`, 실행은 `CarSessionManager.kt`, SDK 연결은 각 구현체가 기준이며, **3절은 그 구현을 정리한 표다.**

## 1. 모듈과 빌드

| 모듈 | 내용 | 비고 |
|---|---|---|
| `app/` | 차 앱. namespace·applicationId `com.maejung` | AAOS |
| `phone/` | 폰 앱 P2 오프라인 UI: [PHONE_APP.md](PHONE_APP.md) | 모듈·UI 구현·debug/폰 AVD 검증 완료. 실제 통신 P3·위치 P4 미구현 |
| `protocol/` | 메시지 data class와 JSON 직렬화. `app`·`phone`이 의존 | 순수 Kotlin(JVM) 모듈, Android 의존성 없음 |
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
- UI는 Jetpack Compose(Material3). 고정한 버전(11/07): Compose BOM `2025.05.01`(ui 1.8.2, material3 1.3.2), activity-compose `1.10.1`, lifecycle `2.9.0`, core-ktx `1.16.0`, kotlinx-coroutines-android `1.10.2`. 지도는 osmdroid `6.1.20`이다.
- JSON은 `kotlinx-serialization-json` 1.8.1(`protocol` 모듈이 `api`로 노출). **메시지 인코딩·디코딩은 `com.maejung.protocol.Protocol`만 쓴다**(11/07 구현 완료):
  - `Protocol.encode(message, ts, sessionId?)` → 봉투 JSON 문자열. `Protocol.decode(text)` → `Received(message, sessionId, ts)`, JSON·봉투 형식이 틀리면 null, 모르는 type은 `Unknown(type, raw)`.
  - 메시지는 `ClientMessage`(보내는 것)·`ServerMessage`(받는 것) sealed 계층의 data class·data object다(`Messages.kt`). 값 상수는 `SessionStates`, `EndReasons`, `EtaSources`, `ErrorCodes`, `CloseCodes`.
  - REST 본문은 `Rest.kt`(`VehicleRegistered`, `PairingCodeIssued`, `PairingRequest`, `PairingResult`, `ApiError`, `ApiPaths`)를 `Protocol.json`으로 읽고 쓴다.
  - 다형 직렬화의 `classDiscriminator`는 모르는 type에서 예외를 던져서 쓰지 않고, type을 먼저 읽어 나눈다.
- 네트워크는 OkHttp 4.12.0(이미 추가됨, WebSocket 확인 완료).
- 서버 주소는 `BuildConfig.SERVER_URL`(`buildFeatures { buildConfig = true }`).
  - debug: `http://10.0.2.2:8080`. `src/debug/AndroidManifest.xml`과 `src/debug/res/xml/network_security_config.xml`에서 `10.0.2.2`만 평문 허용한다.
  - release: Gradle 속성 `maejung.serverUrl`(`-P` 우선, 없으면 `local.properties`)에서 읽는다. 값이 없거나 공백이면 Release 관련 태스크를 실패시킨다. Railway 배포와 운영 주소 연결은 미진행이다.

## 2. 패키지 구조

```
app/src/main/java/com/maejung/
├── MaejungApp.kt                 Application. CarSessionManager를 만들어 들고 있는다(DI 프레임워크 없음)
├── MainActivity.kt               Compose 진입점, 런처, distractionOptimized
├── session/
│   ├── CarSessionMachine.kt      순수 Kotlin 상태 머신. `reduce(CarSession, CarEvent)` → (새 CarSession, 효과 목록). 11/07 구현, 테스트 38개
│   ├── CarSession.kt             `Phase`(Idle / Requested / Sharing / Nearby / Ended(reason)) + 컨텍스트를 담은 `CarSession`, `CarEvent`(3.2), `CarEffect`(3.3)
│   ├── Korean.kt                 이름 조사(민지가/준영이가), 음성 답 해석 `VoiceAnswers.parse`(승인·거절·무시)
│   └── CarSessionManager.kt      Application 범위. 이벤트를 채널 하나로 순서대로 처리하고 효과 실행. session 등을 StateFlow로 공개
├── vehicle/
│   ├── VehicleSignals.kt         인터페이스: gear: Flow<Gear>, doors: Flow<DoorPosition>, readCabinLight(), setCabinLight(CabinSwitch)
│   └── PleosVehicleSignals.kt    Vehicle SDK 구현. 기어·승객 문 구독, 실내등 조회·제어
├── net/
│   ├── SessionTransport.kt       인터페이스: connect(token), send(msg), incoming: Flow<Received>, connection: StateFlow<ConnectionState>, close()
│   ├── WebSocketTransport.kt     OkHttp 구현. 1·2·4·8·15초 재연결. 4401·4409는 transport 자동 재연결 없음
│   ├── FakeSessionTransport.kt   서버 없이 요청·위치·종료를 흉내 낸다(A4). `-Pmaejung.fakeServer=true` 빌드에서만 쓴다(`BuildConfig.FAKE_SERVER`)
│   └── ApiClient.kt              REST: 차 등록, 연결 코드 발급. 서버가 꺼져 있으면 Manager가 5초마다 등록을 다시 시도한다
├── location/
│   ├── LocationSource.kt         인터페이스: fixes: Flow<Fix>, start(), stop()
│   └── AndroidLocationSource.kt  LocationManager(GPS_PROVIDER), 마지막 위치 조회 + minTime 3초, minDistance 0
├── voice/
│   ├── VoiceIO.kt                인터페이스: speak(text), listen(): String?
│   └── PleosVoiceIO.kt           말하기: Pleos TextToSpeech SDK `Mode.ON_DEVICE`, 초기화 전 요청은 모아 두었다가 읽는다. 듣기: Pleos SpeechToText SDK 3.3.1 `Mode.ON_DEVICE`(`STT_SERVICE` 퍼미션), 안내가 끝난 뒤 `request()`, 완료된 `onUpdated`나 10초 시간 초과까지 기다린다. 안드로이드 SpeechRecognizer는 에뮬레이터에서 응답이 없어 쓰지 않는다
├── eta/
│   └── EtaCalculator.kt          NaviHelper ETA가 있으면 source: navi, 없으면 직선거리 × 1.3 ÷ 5.6m/s(20km/h, source: estimate)
├── navi/
│   └── PickupNavigator.kt        NaviHelper 길안내 시작·취소. onRouteStarted·onDrivingInfo의 거리·시간을 StateFlow로 공개
├── alert/
│   ├── PickupNotifications.kt    요청 알림(수락·거절 버튼, 헤드업), 상태 알림(버튼 없음: "가까이 왔어요", "탑승 확인")
│   └── PickupAlertReceiver.kt    알림 버튼 → CarSessionManager에 UserAccept·UserReject
├── service/
│   └── CarSessionService.kt      MainActivity에서 시작하는 START_STICKY 서비스. 위치 권한이 있으면 location, 마이크 권한도 있으면 microphone 타입을 함께 사용. 상시 대기 알림
├── store/
│   └── Prefs.kt                  vehicleToken, 연결된 가족 목록(riderId, riderName), lightBefore 저장. vehicleId는 저장하지 않음
└── ui/
    ├── HomeScreen.kt             연결 상태·가족 목록·연결 코드. "10분 동안 유효" 고정 안내, 남은 시간 카운트다운 없음
    ├── SessionScreens.kt         RequestScreen(승인·거절), SharingScreen(OSM 지도·거리·8방위·ETA·종료), EndedScreen(이유 문구 3초)
    ├── PickupMap.kt              차·탑승자 마커, 차 주변 50m 원, 두 점이 보이도록 지도 조정
    └── CarViewModel.kt           Manager의 StateFlow 구독·입력 전달, 연결 코드 발급·화면 상태 관리
```

- 요청은 다른 앱(내비, 음악 등) 위의 헤드업 알림과 음성으로도 전달한다. 근접·탑승 확인은 버튼 없는 알림과 음성, 길안내 시작은 음성으로 안내한다. 마중 앱이 앞에 있으면 요청·공유·종료 화면도 표시한다. 실제 발화 STT 승인은 사용자 확인 대기다.
- 헤드업 알림 규칙(11/07 다른 에이전트가 남긴 에뮬레이터 기록, DESIGN.md 7절):
  - 채널 중요도 `IMPORTANCE_HIGH`, 카테고리 `CATEGORY_MESSAGE`. `CATEGORY_CALL`로 하면 AAOS가 전화 수신처럼 초록·빨강 아이콘 버튼으로 바꿔 그려서 "수락/거절" 글자가 사라진다.
  - 헤드업은 6~8초 뒤 알림 센터로 들어간다. 그래서 요청은 알림과 동시에 음성으로 묻고 음성 답을 기다린다. 알림 센터에 남은 알림의 버튼도 계속 동작한다.
  - 새 요청을 띄우기 전에 같은 id의 알림을 `cancel`한다. 알림 센터에 이전 알림이 남아 있으면 갱신만 되고 헤드업으로 다시 뜨지 않는다.
  - **버튼 순서**: AAOS 헤드업은 액션을 거꾸로 그리고 마지막 것을 강조(검정)한다. 알림 센터는 순서대로 그린다. 운전자는 주로 헤드업을 보므로 `addAction(수락)` → `addAction(거절)` 순으로 넣어 헤드업에서 "거절 | **수락**"이 되게 한다(알림 센터에서는 "수락 | **거절**"이 된다).
  - 퍼미션 `POST_NOTIFICATIONS`(런타임, 33 이상).
- 연결과 세션은 **Application 범위의 `CarSessionManager`가 소유**한다. 화면 회전·Activity 재생성에 끊기지 않게 하기 위해서다. ViewModel은 구독과 사용자 입력 전달만 한다.
- `MaejungApp`은 `PleosVehicleSignals`·`AndroidLocationSource`·`PleosVoiceIO`·`PickupNavigator`를 주입한다. fake 모드도 서버 transport만 바꾸므로 차량·위치·음성·내비는 실제 구현을 쓴다. `FakeVehicleSignals.kt`와 `FixedLocationSource.kt`는 없다.
- 4401이면 transport 자체는 재연결하지 않지만 Manager가 토큰·가족 목록을 지우고 차를 다시 등록해 연결한다. 4409이면 `REPLACED` 상태로 남는다.
- `spike/VehicleSpikeActivity.kt`는 검증 기록으로 남긴다. 런처는 `MainActivity`로 옮겼다. 스파이크는 런처에서 빼되 adb로 열 수 있게 `exported=true`로 둔다(`adb shell am start -n com.maejung/.spike.VehicleSpikeActivity`). `exported=false`면 셸에서도 열 수 없다.
- `pairing.completed`는 머신을 거치지 않는다. Manager가 Prefs의 가족 목록에 추가하고 HomeScreen에 "{이름} 연결됨"을 3초 보여준다.
- 다시 연결하면 서버가 새 riderId를 주므로, 가족 목록은 같은 riderId **또는 같은 이름**의 예전 항목을 지우고 새 항목을 붙인다(`withMember`). 이전에 쌓인 중복도 읽을 때 마지막 연결만 남긴다(`distinctByName`, `FamilyTest` 4개). 이름이 같은 가족 두 명은 구분하지 않는다. 이전 riderToken은 서버가 폐기하지 못해 계속 유효하다(PROTOCOL.md 1절).

## 3. 상태 머신

`CarSessionMachine`은 Android 의존성이 없는 순수 Kotlin 함수이며 단위 테스트 38개가 있다. 거리 계산(`Location.distanceBetween`)은 머신 밖(Manager)에서 하고 결과를 이벤트로 넣는다.

### 3.1 상태와 컨텍스트

| 상태 | 뜻 |
|---|---|
| Idle | 진행 중 세션 없음 |
| Requested(sessionId, riderName) | 요청 받음, 승인 대기 |
| Sharing(sessionId, riderName) | 위치 공유 중 |
| Nearby(sessionId, riderName, openedDoors) | 50m 이내 + 기어 P에서 진입. 70m까지 유지하며 실내등 ON 지원 시 점등 효과 실행 |
| Ended(reason) | 끝남. 3초 뒤 Idle. reason은 PROTOCOL.md 4절 값 그대로 |

상태와 별개로 머신이 늘 들고 있는 컨텍스트:

- `gear`: 마지막 기어 값
- `distanceM`: 마지막 거리(둘 중 한쪽 위치가 없으면 null)
- `lightInfo`: 마지막 `CabinLightInfo(current: CabinSwitch?, supportsOn: Boolean)`
- `lightBefore`: Nearby에 들어가며 저장한 실내등 원래 값(`CabinSwitch?`). null이면 "켠 적 없음"
- `peer`: 마지막 탑승자 좌표. `navDecided`: 이번 세션의 길안내 여부 판단 완료. `navStarted`: 길안내 시작 효과를 낸 상태(SDK 성공 확인값은 아님)

`CabinSwitch`는 앱 안의 enum(`OFF, ON, DAYTIME_RUNNING, AUTOMATIC`)으로, SDK `LightSwitch`와 1:1로 바꾼다. Boolean으로 줄이면 원래 AUTOMATIC이었던 값을 복구할 수 없어서 enum 그대로 저장한다. 머신이 SDK 타입에 의존하지 않게 하려고 사본을 둔다.

### 3.2 이벤트 (`CarEvent`)

| 이벤트 | 출처 |
|---|---|
| `Server(received)` | `Received` 봉투. `session.state`, `pickup.requested`, `session.ended`, `peer.location`(Manager가 거리 계산 후 `Distance`로도 넣음) |
| `GearChanged(gear)` | VehicleSignals |
| `Door(door, open)` | VehicleSignals, 승객 문 `ROW_1_RIGHT`·`ROW_2_LEFT`·`ROW_2_RIGHT`만. SDK 위치가 0보다 크면 `open=true`, 0이면 false, null이면 무시 |
| `CabinLight(info)` | Manager가 `checkCabinLightSwitchCapabilities` + `getCabinLightSwitch`로 읽어 넣는다. 시점: 앱 시작, 그리고 Sharing에 들어갈 때마다(StartLocationSharing 실행 시). 실내등 "스위치" 구독 API는 문서에 없다(`registerCabinLightState`는 실제 점등 상태 `LightState`용이라 저장·복구에 쓰지 않는다) |
| `Distance(meters?)` | Manager. 차·탑승자 위치가 바뀔 때마다 |
| `UserAccept`, `UserReject` | 알림·화면 버튼 또는 음성 답 |
| `UserEnd` | 공유 화면의 종료 버튼 |
| `EndedTimeout` | Ended 진입 3초 뒤 Manager가 넣음 |

### 3.3 효과 (`CarEffect`)

`Send(message)`, `SetCabinLight(value: CabinSwitch)`, `SaveLightBefore(value: CabinSwitch?)`, `Speak(text)`, `Listen`, `StartLocationSharing`, `StopLocationSharing`, `ShowRequestAlert(riderName)`, `DismissRequestAlert`, `Notify(text)`(버튼 없는 헤드업), `StartNavigation(lat, lng, riderName)`, `StopNavigation`.

- **길안내 규칙**: Sharing에서 처음 null이 아닌 `Distance`를 받으면 `navDecided = true`. **500m 이상이고 `peer`가 있으면** `StartNavigation(peer)` 한 번, `navStarted = true`. 500m 미만이거나 좌표가 없어서 시작하지 못해도 그 세션에서는 다시 판단하지 않는다. 탑승자 이동에 따른 재탐색도 없다. 종료로 가는 모든 전이에서 `navStarted`면 `StopNavigation`.
- **ETA**: `PickupNavigator.eta`가 있으면 `onRouteStarted` 또는 `onDrivingInfo`의 남은 거리·시간을 쓴다(`source: navi`). 내비 값이 없고 양쪽 위치가 있으면 직선거리 × 1.3을 도로 거리로 보고 ÷ 5.6m/s로 시간을 추정한다(`source: estimate`, 정수 반올림). 둘 다 없으면 ETA는 null이다. 공유 루프에서 3초마다 확인하고, 최초·30초 경과·시간 20% 초과 변화·출처 변경 때 `eta.update`를 보낸다. 내비 오류 콜백은 로그만 남기므로 오류 자체가 ETA를 비우지는 않는다.

- 실내등 저장·복구 판단은 **머신이 한다.** 효과 실행기는 `SetCabinLight`를 그대로 SDK 호출로 바꿀 뿐이다.
- 복구는 두 가지다.
  - **복구(공유 유지)**: Nearby → Sharing으로 돌아갈 때. `lightBefore`가 null이 아니면 `SetCabinLight(lightBefore)` → `SaveLightBefore(null)` → `Send(vehicle.light {on: lightBefore == ON})`, `lightBefore = null`.
  - **복구(종료)**: Ended로 갈 때. `SetCabinLight(lightBefore)` + `SaveLightBefore(null)`만 내고 **Send는 하지 않는다.** 서버는 SHARING에서만 `vehicle.light`를 받기 때문이다.
- `SaveLightBefore`는 Manager가 Prefs에 저장한다. 앱 시작부터 Prefs 값을 머신의 `lightBefore` 초기값으로 넣는다. `session.state none`을 받은 뒤 Idle이고 값이 남아 있으면 Manager가 그 값으로 복구하고 지운다. `sharing` 복원에서는 저장값을 유지한다. Nearby 진입 시 현재값을 모르면 OFF를 저장한다. SDK set 실패는 로그로 남기며 효과 실행은 계속한다.

### 3.4 전이 표

| 현재 | 이벤트 | 다음 | 효과 |
|---|---|---|---|
| Idle | `pickup.requested` | Requested | ShowRequestAlert, Speak("{이름}가 데리러 와 달래요. 갈까요?"), Listen |
| Requested | UserAccept(알림·화면 버튼, 또는 음성 "응/그래/갈게/좋아" 포함) | Sharing | DismissRequestAlert, Send(`pickup.accept`), StartLocationSharing |
| Requested | UserReject(알림·화면 버튼, 또는 음성 "아니/안 돼/싫어" 포함) | Ended(rejected) | Send(`pickup.reject`), 종료 공통 |
| Sharing | 이 세션에서 처음 null이 아닌 Distance(`navDecided == false`) | Sharing | `navDecided = true`. `distance ≥ 500`이고 탑승자 좌표가 있으면 StartNavigation(peer), `navStarted = true`, Speak("{이름}에게 가는 길을 안내할게요"). 500m 미만이면 이 세션에서는 다시 판단하지 않는다 |
| Sharing | 조건 `distance ≤ 50 && gear == P` 충족(Distance 또는 GearChanged 이벤트) | Nearby | `lightInfo.supportsOn`이면: `lightBefore == null`일 때만 `lightBefore = lightInfo.current ?: OFF`, SaveLightBefore(lightBefore). 그다음 SetCabinLight(ON), Send(`vehicle.light on`). 공통: Notify("{이름}가 가까이 왔어요"), Speak(같은 문장) |
| Nearby | Distance 또는 GearChanged에서 `distance > 70 \|\| distance == null \|\| gear != P` | Sharing | 복구(공유 유지) |
| Nearby | Door(승객 문, open=true) | Nearby | openedDoors에 추가 |
| Nearby | Door(openedDoors에 있는 문, open=false) 그리고 `gear == P && distance ≤ 30` | Ended(boarded) | Send(`session.boarded`), 종료 공통, Notify·Speak("{이름} 탑승 확인, 위치공유를 끝낼게요") |
| Requested·Sharing·Nearby | UserEnd | Ended(manual) | Send(`session.end manual`), 종료 공통 |
| Requested·Sharing·Nearby | `session.ended(reason)` | Ended(reason) | 종료 공통 |
| Requested·Sharing·Nearby | `session.state none` | Ended(peer_lost) | 종료 공통 |
| Idle | `session.state requested` | Requested | 효과 없음(요청 알림·음성·Listen을 다시 실행하지 않음) |
| Idle·Requested | `session.state sharing` | Sharing | (Requested였으면 DismissRequestAlert), StartLocationSharing |
| Ended | EndedTimeout | Idle | - |
| Ended | `pickup.requested` | Requested | 위 Idle 행과 같음 |

- **종료 공통** = DismissRequestAlert(Requested에서 끝날 때), 복구(종료), StopLocationSharing(Sharing·Nearby에서 끝날 때만), `navStarted`면 StopNavigation. 길안내 판단과 근접 조건이 같은 Distance에서 충족되면 한 번의 reduce에서 함께 처리한다.
- 표에 없는 조합은 phase를 바꾸지 않고 효과도 없다(예: Idle에서 Door, Sharing에서 UserAccept). 다만 기어·거리·실내등·탑승자 좌표 컨텍스트는 전이 판단 전에 갱신한다.
- Nearby에 들어오기 전에 열린 문은 세지 않는다. Nearby를 나가면 openedDoors를 비운다.
- Nearby 진입 때 `lightInfo.supportsOn`이 true가 아니면 실내등 저장·점등과 `vehicle.light`를 생략한다. 근접 안내·탑승 판정은 같다. 이미 남아 있는 `lightBefore`의 복구는 별도 규칙이다.
- 실내등이 원래 켜져 있었으면(`lightBefore == ON`) 복구해도 켜진 채로 둔다. AUTOMATIC이었으면 AUTOMATIC으로 되돌린다.
- `lightBefore`가 이미 있으면(앱 재시작 후 Prefs에서 복원) Nearby에 다시 들어가도 덮어쓰지 않는다.
- 음성 인식이 실패하거나 지원되지 않으면 버튼만으로 동작해야 한다. 음성 결과는 `VoiceAnswers.parse`로 해석하고(거절 단어를 먼저 본다, "안 갈게" 때문), 승인·거절 단어가 다 없으면 무시한다. "어"는 "어디야"에도 들어 있어 승인 단어에서 뺐다.
- 안내 문장의 이름 조사는 받침에 따라 고른다(민지가 / 준영이가, 민지에게 / 준영이에게).
- 새 요청(Requested 진입) 때 탑승자 좌표·거리·길안내 판단을 비운다. 실내등 원래 값(`lightBefore`)은 비우지 않는다(앱 재시작 복원값일 수 있다).
- 효과 순서는 테스트로 고정되어 있다. 종료 전이는 `[서버에 보낼 메시지] → DismissRequestAlert → 실내등 복구 → StopLocationSharing → StopNavigation → [안내]` 순이다.
- 위치 전송(StartLocationSharing)은 마지막 차 위치가 있으면 정차 여부와 관계없이 3초마다 보낸다. 10m 이동 감지는 따로 구현하지 않았다. StopLocationSharing은 전송 루프와 상대 위치·ETA·방위 표시를 비우며, Application의 GPS 구독은 계속된다.

## 4. 화면 원칙 (주행 중)

- **OSM 지도가 구현돼 있다.** `SharingScreen` 왼쪽 절반은 osmdroid `MapView`, 오른쪽 절반은 거리·8방위·ETA·종료 버튼이다. 탑승자는 하늘색(#00AAD2) 원, 차는 흰 테두리 남색(#002C5F) 원, 차 주변 50m는 옅은 하늘색 원으로 표시한다(폰 지도와 같은 색). 두 점이 있으면 모두 보이도록 범위를 맞추고, 한 점이거나 두 점이 정확히 같은 좌표면 그 점을 가운데 두고 확대 18로 맞춘다(11/08, 같은 좌표에서 범위 크기 0으로 세계 지도까지 축소되던 문제 수정).
- 타일은 `TileSourceFactory.MAPNIK`, User-Agent는 `applicationId/versionName`, 출처는 `CopyrightOverlay`, 캐시는 앱 cache 디렉터리다. 이는 현재 설정 설명이며 외부 타일 이용 규정을 이번 문서 갱신에서 재검증한 것은 아니다.
- 방향 화살표 대신 북쪽 기준 8방위 이름을 표시하고, 5m 미만이면 숨긴다. ETA는 `길안내 기준` 또는 `예상`으로 구분한다. 실내등 안내는 세션의 지원 여부·저장값으로 판단하며 실제 점등 센서 확인값은 아니다.
- **대기 중 혜택 데모**(11/08, `ui/WaitingOffers.kt`): Sharing이고 기어가 P일 때만 오른쪽 패널의 종료 버튼 위에 혜택 2개를 보여준다. P에서 벗어나거나 Nearby가 되면 숨긴다(주행 중 표시 금지, 근접 때는 아이 찾기에 집중). 데이터는 지어낸 가게·혜택의 고정 목록이고 "제휴 예시"를 함께 표시한다. 누르는 곳이 없어 화면당 주요 버튼 수는 그대로다. 광고 SDK·서버 통신은 없다(차에는 GMS도 없다). 표시 조건은 `WaitingOffersTest` 3개로 확인하고, 11/08 에뮬레이터에서 P·Sharing 표시와 Nearby 숨김을 봤다. 기어 D 전환 시 숨김은 단위 테스트 근거다.
- 요청 버튼은 높이 180dp·글자 40sp, 공유 종료 버튼은 160dp·36sp다. 홈의 닫기·fake 버튼은 120dp·32sp이고 일부 보조 문구는 28sp다. 화면당 주요 버튼은 최대 2개다.
- 색과 로고는 폰 앱과 같은 마중 브랜드를 쓴다(`ui/CarTheme.kt`, 11/07). 주행 중 화면이라 어두운 남색(#0A1B30) 배경 위에 진주색 글자를 쓰고, 남색 위에서 보이도록 주 색(수락·가족 연결 버튼, ETA, 연결 코드)은 하늘색, "실내등을 켰어요"는 호박색(#E3B461)이다. 홈·요청·종료 화면 위쪽에 폰과 같은 픽셀 로고(남색 칸은 진주색으로 바꿈)를 둔다. 공유 화면의 거리·방향은 폰 지도 아래 안내 칸처럼 패널에 묶고, 공유 끝내기는 테두리 버튼이다. OSM 타일에 별도 다크 처리는 없다. 헤드업 알림은 시스템이 그려서 이 색을 따르지 않는다.

## 5. 퍼미션

- 매니페스트에 선언된 차량 권한: `pleos.car.permission.` 접두사의 CAR_DRIVE, CAR_CLIMATE, CAR_LIGHTS, CAR_WINDOWS, CAR_DOORS, CAR_SEATS, CONTROL_CAR_CLIMATE, CONTROL_CAR_WINDOWS, CONTROL_CAR_LIGHTS. 통신용 `android.permission.INTERNET`도 있다.
- `ACCESS_FINE_LOCATION`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`도 이미 선언돼 있고 `MainActivity`가 미허용 권한을 런타임 요청한다. 요청 결과 콜백에서 위치 구독과 서비스 시작을 다시 시도한다. 에뮬레이터 수동 부여 예: `adb shell pm grant --user 10 com.maejung android.permission.ACCESS_FINE_LOCATION`(**`--user 10` 필수**, 11/07 기록).
- Pleos SDK 권한: `pleos.car.permission.TTS_SERVICE`(출력), `STT_SERVICE`(인식), `NAVI_ROUTE`(조회), `NAVI_ROUTE_SEARCH`(길안내 시작·취소).
- 서비스 권한: `android.permission.FOREGROUND_SERVICE`·`FOREGROUND_SERVICE_LOCATION`·`FOREGROUND_SERVICE_MICROPHONE`. 서비스 선언은 `foregroundServiceType="location|microphone"`이다. 위치 권한 없이는 시작하지 않고, 마이크 권한이 없으면 실행 시 location 타입만 사용한다.
- 창문·공조·좌석 관련 권한과 exported spike Activity는 아직 남아 있다. 기본 런타임의 차량 신호는 기어·문·실내등만 사용하며, 공조·창문 제어와 착석·벨트 관측은 spike 기록과 구분한다.

## 6. 에뮬레이터 검증

- 이 절의 에뮬레이터 결과는 다른 에이전트가 남긴 2026-11-07 기록이다. 이번 문서 갱신에서는 테스트·빌드·에뮬레이터를 새로 실행하지 않았다.
- 앞선 세션에서 JBR 21, offline, `--rerun-tasks`로 app 46개(상태 머신 38·한국어 3·ETA 5), protocol 별도 9개, server 34개, 합계 89개 테스트가 실패 0개였고 debug assemble도 성공했다. 단위 테스트 결과와 차량 SDK·실제 발화 검증은 구분한다.
- P2 메인의 새 회귀 실행: JBR 21, `:protocol:test :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --offline --no-daemon --console=plain` → BUILD SUCCESSFUL(12초·47개 태스크 실행), app 46 + protocol 9 = 55개, 실패·오류·건너뜀 0. server는 소스 변경 없이 미재실행(34개는 이전 결과). 폰은 별도로 16개 테스트·debug 조립·AVD 설치·화면/복원 검증을 완료했다(PHONE_APP.md 6절). 폰 P2는 실제 차에 요청·알림을 보내지 않는다.
- 실행·설치·Virtual Console 사용법은 루트 `CLAUDE.md`.
- 기어: Virtual Console Gear Selection(P/R/N/D). 문: BODY 패널 FL/FR/RL/RR 클릭(열림↔닫힘). 브라우저 폭 1600px 이상에서 보인다.
- 차 위치 주입: `adb -s emulator-5554 emu geo fix <경도> <위도>`(경도가 먼저). 11/07 C1 기록에서 GPS 구독 수신과 정차 중 약 3초 간격 수신이 확인됐다(DESIGN.md 7절). 현재 `AndroidLocationSource`도 LocationManager를 사용한다.
- 실내등은 Virtual Console에 상태 표시가 없다. 앱 로그의 `getCabinLightSwitch` 조회값으로 확인한다.
- 데모 좌표는 TASKS.md D1.
- Pleos TTS는 초기화·발화 완료 콜백, Pleos STT는 `onReady`까지의 기록이 있다. 실제 사람 발화가 승인·거절로 처리되는지는 사용자 확인 대기다.

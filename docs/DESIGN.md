# 마중 설계

기획 원본(시나리오, 사업성, 개인정보 원칙)은 Obsidian `01. Projects/HMG 차량용 앱 공모전/HMG 차량용 앱 공모전 - 마중 기획서 초안.md`에 있다. 이 문서는 코드와 같이 바뀌는 설계만 다룬다.

**구현 기준: 2026-11-07, P3 완료 커밋 `f5c7502` 기반 P4 debug 범위 완료.** main 최종 QA signoff와 전체 243개 GREEN·최종 증분 실행·최신 APK 안내/마커·양쪽 공유·알림 종료·app_closed 서버 종료 근거를 반영했다. 독립 검토 결과는 최종 QA 근거와 함께 관리한다. 현재 구조와 기존 검증 이력을 구분하고 통신 계약은 [PROTOCOL.md](PROTOCOL.md)를 따른다. 기본 차 앱(`MainActivity` → `CarSessionManager`)과 과거 `spike/VehicleSpikeActivity` 기록도 구분한다. P2 UI와 P3 실제 통신·상태 복원·연결 예외·미리보기 격리의 통과는 이전 이력이다(8절). P4 근거는 PHONE_APP.md 8절에 기록한다. 실물 기기·release APK 런타임·API 26 설치/런타임과 외부 SDK·마켓·타일 이용 규정은 이번 검증에 포함하지 않는다.

## 폰 UI 확정 시안 (2026-11-07, P2 승인·P4 실제 지도 debug 검증 완료)

- **P2 사용자 디자인 승인·UI 검증은 기존 이력이며 P4 실제 지도는 debug 검증을 완료했다.** 로컬 도식·샘플 좌표는 debug `PreviewSharingMap.kt`에 유지하고, 기본 실제 화면의 `SharingMap.kt`는 osmdroid 지도를 사용한다. 아래 샘플 값은 미리보기 전용이며 기존 QA를 P4 GPS·지도나 API 26 런타임 검증으로 해석하지 않는다.
- 딥 네이비 `#002C5F`·펄 `#F7F8FA`·시안 `#00AAD2`를 기본 색으로 사용한다.
- 픽셀 마크·"마중" 브랜드와 네이티브 은색 차량 일러스트를 사용한다. P3 상단은 실제 연결 상태를 표시하며 debug에서만 별도 미리보기 진입을 제공한다. 미리보기 안의 작은 오프라인 배지·장면 메뉴·행동은 실제 연결과 분리한다.
- 연결 코드는 포커스 중 단일 편집기로 표시하고, 포커스가 없고 6자리 이하 ASCII 숫자이면 여섯 칸으로 표시한다. 잘못된 원문은 단일 편집기에 그대로 보이며 자르거나 필터링하지 않는다.
- 실제 지도는 북쪽을 위로 두고 동일 세션의 WGS84 좌표로 차 네이비 `#002C5F`·탑승자 시안 `#00AAD2` 마커를 표시한다. P2 근접 예시는 차 좌표를 고정하고 아이가 가까워지는 로컬 그림이다.
- **지도 아래** HUD에 내 GPS와 차 사이 직선거리·‘이동 기준’ 화살표·수신 `peer.eta`만의 도착예상을 모은다. 실제 화면에는 좌표 숫자·지도 준비 문구·샘플 수치를 표시하지 않는다. 120m·약 2분, 20m·약 30초는 debug 미리보기 값이다.
- 공유·근접 지도는 최소 높이를 유지하면서 화면에 남는 높이를 채운다. 종료 버튼과의 큰 빈 공간을 없애고 작은 화면·큰 글꼴·가로 화면에서는 스크롤을 유지한다.
- P4 `SharingMap`은 차와 같은 osmdroid `6.1.20`(OpenStreetMap), User-Agent·`CopyrightOverlay`·`clipToBounds`를 적용한다. main은 같은 fix로 정렬한 양쪽 실제 마커·거리 일치, 실제 폰 GPS 이동·근접·탑승 종료와 320dp/글꼴 1.3의 전체 출처 표시·스크롤 종료 버튼 접근을 확인했다. 상세 구조와 근거는 PHONE_APP.md 5·8절이다.
- 위치 없음 배너는 차·내 위치가 모두 없을 때만 표시해 단일 실제 마커를 가리지 않는다. 최신 `final-permission-denied-marker`에서 네이비 차 마커가 가려지지 않고 권한·알림 안내는 지도 밖에 표시됨을 main이 확인했다.
- 나침반의 12시는 GPS 이동 방향(course)이며, 차 방위에서 course를 뺀 상대각을 표시한다. GPS bearing은 유효 속도의 임계 조건을 만족할 때만 사용하고 아니면 유효 GPS 변위로 구하며 기기 방향 센서를 쓰지 않는다. 정지 bearing 0을 걸러내는 수정 후 통합 GREEN과 최신 학원 정지 화면의 `—`를 확인했다. 미리보기의 차 방위 80°·course 35° → 오른쪽 앞 45°는 샘플이다.
- 유효 course가 없으면 `—`를 표시하고 방향 화살표를 숨긴다. 실제 거리·방향은 필요한 GPS 값이 있어야 표시하며 ETA는 수신값이 있을 때만 표시한다. P2 정보 없음 예시는 차·아이 마커와 거리·나침반·ETA를 모두 숨긴다.
- 실내등 켜짐 예시에서는 지도 아래에 "실내등 켜진 차를 찾아주세요" 안내를 둔다.
- 차 앱도 같은 색·픽셀 로고를 쓴다(11/07, `app/.../ui/CarTheme.kt`). 주행 화면이라 어두운 남색 배경에 진주색 글자, 주 색은 하늘색, 실내등 안내는 호박색이며 지도 마커는 폰과 같이 차 남색·탑승자 하늘색이다. 상세는 CAR_APP.md 4절.
- 로컬 참고: [HTML 시안](../.omc/design/phone-relative-compass-20261107/index.html), [시안 설명](../.omc/design/phone-relative-compass-20261107/brief.md), [가까이 화면](../.omc/design/phone-relative-compass-20261107/exports/nearby.png).
- 위 자료는 Git에서 무시되는 `.omc` 시안 산출물이며 제품용 자산이 아니다.

## 0. 설계 원칙

- **양쪽 동의로 시작하고, 어느 쪽이든 끝낼 수 있다.** 탑승자가 요청하고 운전자가 승인해야 위치가 오간다.
- **위치는 공유 중에만 서버로 보낸다.** 차·폰 전송과 서버 중계는 SHARING에서 동작하며 마지막 실제 fix를 정지 중에도 3초마다 보낸다. 폰 GPS·위치 FGS도 Sharing·위치 권한 조건에서만 실행한다. 서버는 좌표를 저장하지 않고 상대에게 중계하며, 메모리 세션에는 마지막 위치 수신 시각만 남긴다. 좌표를 디스크·서버 로그에 남기지 않는다. 차 앱의 GPS 구독은 Application 시작부터 계속되므로 폰과 수집 수명이 다르다.
- **종료는 자동이 기본, 수동은 예비다.** 차 앱은 탑승 판정으로 끝내고, 서버는 요청 승인 대기 5분·공유 중 어느 한쪽의 위치 미수신 2분 초과·요청 생성 후 60분을 5초마다 검사해 종료한다. 수동 종료도 가능하다.
- **기본 런타임의 차량 제어는 실내등 하나만 쓴다.** 기어 P·근접 조건에서 켜고 Nearby 이탈이나 종료 시 이전 설정값으로 복구한다. 읽는 신호는 기어·승객 문·실내등이다. 좌석·벨트 관측과 공조·창문·앰비언트 제어는 spike에만 남아 있다. 대기 모드와 약속 색 앰비언트는 기본 앱에서 제외했다(2026-11-06).
- 주행 중에는 조작을 최소화한다. 승인은 음성 또는 큰 버튼 하나.

## 1. 구성

```
[폰 앱: P4 debug 검증 완료] ──HTTPS/WebSocket──► [마중 서버: Spring Boot, 메모리 세션] ◄──WebSocket── [차 앱 (Pleos)]
 요청·종료, Sharing GPS·OSM              연결 코드, 세션, 위치 중계, 종료 시 삭제     승인, 차 위치, ETA, 실내등, 탑승 판정
```

- 차에는 GMS가 없어 FCM을 못 쓴다. 차 앱은 서버 WebSocket에 붙어 요청을 받는다.
- 저장소 구성은 `app/`·`phone/`·`protocol/`과 별도 Gradle 프로젝트 `server/`다. 폰은 `MaejungPhoneApp` → `PhoneSessionManager`가 Application 범위의 연결·세션·P4 내 위치/heartbeat를 소유하고 Activity가 구독한다. 토큰·이름은 SharedPreferences, 세션은 서버의 첫 `session.state`로 복원한다. 서비스가 `location/AndroidLocationSource`의 `LocationManager.GPS_PROVIDER` fix를 manager에 전달하고 Activity는 권한·가시성에 따른 실행 조건을 조정한다. GMS 위치원은 쓰지 않는다. 상세는 [PHONE_APP.md](PHONE_APP.md) 4·5절이다.
- 폰의 위치 권한은 픽업 클릭에서 fine·coarse를 함께 요청하며 coarse도 Sharing·FGS 자격에 유효하다. 거부해도 위치 불가 안내와 픽업 요청을 유지한다. 이 API 36 에뮬레이터의 coarse-only는 초기 GPS 지연 후 실제 난독화 fix를 받았고 화면에 1286m가 표시됐다. 적시·정확한 이동에는 fine이 필요하며 실제 fix 전에는 가짜 위치를 만들지 않는다. 첫 fix 전부터 정밀도 안내와 알림 차단 안내를 독립적으로 표시하며 최신 두 거부 상태 캡처에서 안내를 확인했다. Sharing 서비스는 ongoing "엄마 차와 위치 공유 중"·"끝내기" 알림을 쓴다. API 34의 백그라운드 수락에서는 새 위치 FGS를 시작하지 않고 Activity가 보일 때 재시도한다. 정상 시작한 Sharing 서비스는 백그라운드·화면 꺼짐에도 GPS를 유지한다.
- P2 미리보기는 debug에서 명시적으로 선택한다. 저장된 장면이 있어도 앱 시작은 실제 화면이며, 실제 페어링·요청·공유 중에는 미리보기로 숨길 수 없다. 미리보기 콜백은 실제 세션 메시지를 보내지 않는다. release는 미리보기 없이 실제 연결을 사용하며 APK 런타임은 미검증이다.
- 차 앱에는 OSM 지도(osmdroid `6.1.20`), Pleos ON_DEVICE TTS/STT, LocationManager GPS, NaviHelper 길안내·ETA가 구현돼 있다. 지도(A9)는 구현 완료, 음성(A6)은 구현 완료·실제 발화 검증 대기로 구분한다. 차 위치를 Pleos FusedLocation으로 자동 교체하는 로직은 없다.
- 서버 Docker 설정은 작성됐지만 Docker Hub timeout으로 이미지 빌드 성공을 확인하지 못했다(S4 빌드 검증 대기). Railway 배포(S5)는 미진행이다.
- 상세: 통신 [PROTOCOL.md](PROTOCOL.md), 서버 [SERVER.md](SERVER.md), 차 앱 [CAR_APP.md](CAR_APP.md), 폰 앱 [PHONE_APP.md](PHONE_APP.md).

## 2. 세션 흐름 (개요)

차 앱 구현은 `CarSessionMachine.kt`·`CarSessionManager.kt`, 서버 구현은 `SessionService.kt`, 폰은 `PhoneSessionManager.kt`가 담당한다. 아래는 그 개요이며 상세 계약은 [PROTOCOL.md](PROTOCOL.md), 폰 상태 전이는 [PHONE_APP.md](PHONE_APP.md) 4절에 있다. 폰은 연결 완료와 첫 `session.state` 동기화 후 요청을 허용하고, 일반 끊김·재연결에도 최초 요청의 10초 응답 기한을 유지한다. 만료되면 Idle·연결 안내·미동기화 상태가 되며 서버의 새 스냅샷을 받은 뒤 새 요청을 허용한다. 종료 안내는 3초 후 홈으로 복귀한다. 일반 끊김은 재연결하되 heartbeat는 공유 스냅샷 동기화 후 재개한다. 4401은 자격 삭제·연결 화면, 4409는 자격 보존·명시적 다시 연결이며 두 코드에서 GPS·위치 서비스·heartbeat·자동 재접속을 중단한다. 화면/알림의 오프라인 종료 시도는 활성 상태와 안내를 유지해 재연결 후 재시도한다.

| 단계 | 서버 상태 | 차 앱 상태 | 차 앱 | 폰 앱 (P3/P4) |
|---|---|---|---|---|
| 요청 | REQUESTED | Requested | 알림 + 음성, 승인/거절 | "엄마 차에 알렸어요" |
| 공유 | SHARING | Sharing | OSM 지도·상대 위치·방위·ETA | GPS·위치 FGS, OSM 지도와 GPS 직선거리·이동 기준 방향·수신 ETA·실내등 |
| 근접 | SHARING | Nearby (50m 이내 + 기어 P에서 진입) | "○○가 가까이 왔어요", ON 지원 시 실내등 점등 | "실내등 켜진 차가 엄마 차예요" (ON 지원 시) |
| 종료 | 세션 삭제 + `session.ended` 통지 | Ended(reason) | 실내등 복구, 이유별 화면(3초). 탑승 때는 알림·음성도 안내 | 이유별 안내 |

- 종료 이유는 하나의 Ended 상태 안에서 `reason`으로 구분한다: `boarded`, `manual`, `rejected`, `request_timeout`, `peer_lost`, `max_duration`(PROTOCOL.md 4절).
- Nearby에서 거리가 70m를 넘거나 null이 되거나 기어가 P가 아니게 되면 Sharing으로 돌아가고 실내등을 복구한다(50/70m 이중 기준으로 경계에서 깜빡임 방지).
- 공유 중 처음 null이 아닌 거리를 받았을 때 한 번만 길안내 여부를 판단한다. 500m 이상이고 탑승자 좌표가 있으면 NaviHelper로 그 좌표까지 안내한다. 처음 거리가 500m 미만이면 나중에 멀어져도 다시 시작하지 않는다. 요청 후 탑승자 이동에 따른 목적지 갱신도 없다. 시작 효과를 낸 세션은 종료 때 안내를 취소한다.
- 차의 ETA는 내비의 `onRouteStarted`·`onDrivingInfo` 값이 있으면 그대로 사용한다. 없으면 양쪽 위치의 직선거리 × 1.3 ÷ 5.6m/s로 추정하며, 화면과 메시지에서 `navi`·`estimate`를 구분한다. 내비 오류 콜백은 기존 ETA를 비우지 않는다. 폰은 `peer.eta` 수신값만 표시하고 자체 ETA를 만들지 않는다.
- 종료되면 서버는 세션·양쪽 인덱스·마지막 수신 시각 기록을 메모리에서 지운다. 차 앱은 전송을 멈추고 상대 위치·ETA·방위 표시를 비운다.
- 폰의 실행 중인 위치 서비스는 최근 앱 제거의 `onTaskRemoved`에서 `appClosed()`를 호출해 `session.end(app_closed)`를 최선 노력으로 보내고 GPS·heartbeat·소켓 재시도를 중단한다. 서버는 이를 `manual`로 알린다. 오프라인 종료는 큐에 남기지 않으며 강제 종료·작업 관리자 Stop은 콜백을 보장하지 않는다. Requesting·위치 권한 없음에는 위치 FGS도 없으므로 스와이프 종료를 보장하지 않는다. 공유 heartbeat 미수신 2분 초과 만료가 대안이다.

## 3. 탑승 판정

Nearby 상태에서 아래가 모두 맞으면 Ended(boarded).

1. 기어 `GEAR_PARK`
2. Nearby에 들어온 뒤 승객 문(`ROW_1_RIGHT`, `ROW_2_LEFT`, `ROW_2_RIGHT`) 중 하나의 열림 이벤트를 받고 같은 문의 닫힘 이벤트를 받음(SDK 위치가 0보다 크면 열림, 0이면 닫힘, null은 무시)
3. 마지막 탑승자 위치와 차 위치가 30m 이내 (GPS 오차를 고려한 초기값)

- Nearby 전에 열린 문이나 다른 문의 닫힘은 판정에 쓰지 않는다. Nearby를 나가면 열린 문 기록을 비운다.
- 기본 `PleosVehicleSignals`는 착석·벨트를 구독하지 않는다. 이 신호의 조회·로그는 spike에서만 수행했으며 당시 기본값은 승객석 착석 true였다(7절).
- 판정 직후 확인 질문 없이 `session.boarded`를 보내고 바로 끝낸다. 폰의 실제 종료 수신·재요청 처리는 P3 범위이며 최종 실행 근거를 별도로 기록한다. P2 종료 표시는 미리보기다.

## 4. 차량 제어: 실내등 차 찾기 (2026-11-06 확정)

- Nearby에 들어갈 때 ON 지원 여부를 보고 마지막 조회값을 저장한 뒤 켠다. 현재값이 null이면 OFF를 저장한다. Ended나 Sharing으로 나갈 때 저장한 enum 값으로 되돌린다. 원래 ON이면 ON, AUTOMATIC이면 AUTOMATIC으로 복구한다.
- 현재 값(`LightSwitch`: OFF, ON, DAYTIME_RUNNING, AUTOMATIC)과 지원 여부는 앱 시작 때와 Sharing 진입 때 `checkCabinLightSwitchCapabilities` + `getCabinLightSwitch`로 읽는다. 원래 값은 enum 그대로 저장하고, Boolean으로 줄이지 않는다. 저장·복구 판단은 차 앱 상태 머신이 한다(CAR_APP.md 3절).
- `registerCabinLightState(LightStateListener)`도 있지만 실제 점등 상태(`LightState`)용이다. 스위치 설정값 구독 API는 문서에 없어서 쓰지 않는다.
- ON 지원 여부가 true가 아니면 Nearby 진입의 저장·점등·`vehicle.light`를 생략하고 알림·음성과 탑승 판정을 유지한다. P3 폰은 해당 세션의 실제 `peer.light`가 true일 때만 실내등 안내를 강조한다. P2 미리보기의 실내등 샘플과 구분한다.
- 원래 값은 Prefs의 `lightBefore`에도 저장한다. 재시작한 뒤 진행 세션이 없으면(`session.state none` + Idle) Manager가 복구하고 지운다. 공유 복원에서는 그 값을 유지해 다시 점등하더라도 덮어쓰지 않는다.
- Nearby → Sharing 복구는 `SetCabinLight` → `SaveLightBefore(null)` → `vehicle.light(on = 원래 값이 ON)` 순이다. 종료 복구 때는 `vehicle.light`를 보내지 않는다. SDK set 실패는 로그로 남기고 처리를 계속하므로 상태 머신 효과와 실제 점등 성공은 구분한다.
- 비상등은 Light API에 get·set이 모두 없다(Virtual Console에만 Hazard Lights 카드가 있다). 그래서 실내등을 쓴다.
- 하향등·상향등·안개등·미등 set도 있지만, 정차 중 사람을 향해 켜면 눈부시고 위험해서 쓰지 않는다.
- 실내등은 낮에는 밖에서 잘 안 보인다. 주 사용 장면(저녁~밤 하원)에 맞춘 기능이다.

### 쓰지 않는 제어 (검증 기록)

대기 모드(화면·공조 절약)와 약속 색 앰비언트는 빼기로 했다. 나중을 위해 검증 내용만 남긴다.

- 모든 제어 전에 `checkXxxCapabilities`로 지원 여부와 범위를 확인한다.
- **영역은 캐퍼빌리티가 돌려준 값을 그대로 쓴다.** 앰비언트 라이트와 내기순환은 좌석별이 아니라 차량 전체 영역이라 `SeatArea(ROW_1_LEFT)`로 호출하면 `area ID: 0x1 not supported`가 난다.
- 앰비언트 set에는 `CONTROL_CAR_LIGHTS`가 필요하다(문서에는 CAR_LIGHTS만 적혀 있지만 없으면 SecurityException).

## 5. Vehicle SDK 매핑

`ai.pleos.playground:Vehicle:2.3.2`. 진입점 `Vehicle(context)` → `initialize()` → `getXxx()` → 사용 → `release()`. 조회·설정 결과는 비동기 `onComplete`/`onFailed`, 구독 신호는 리스너로 받는다.

### 기본 런타임에서 사용하는 API

| 도메인 (getter) | API | 퍼미션 | 용도 |
|---|---|---|---|
| Gear (`getGear`) | `registerGearSelection(GearSelectionListener)` → `onSelected(VehicleGear?)` | CAR_DRIVE | 탑승 판정 |
| Door (`getDoor`) | 승객 문 3개에 `registerDoorPosition(DoorArea, DoorPositionListener)` → `onPositionUpdated(Int?)`. 종료 시 `unregisterDoorPosition` | CAR_DOORS (normal) | 탑승 판정. 문 캐퍼빌리티 조회는 spike에서만 수행 |
| NaviHelper (`ai.pleos.playground:NaviHelper:3.0.5`) | `NaviHelper(context)` → `initialize(onConnected, onFailed)` → `addListener(NaviHelperEventListener)`. `requestRoute(RouteInfo(latitude, longitude, poiName, poiId, address, poiSubId))`, `cancelRoute()`. 콜백 `onRouteStarted(RouteStartInfo)`, `onDrivingInfo(DrivingInfo)`(`destination.distance` m, `destination.duration` 초), `onRouteCancelled()`, `onError(NaviErrorCode)`. 리스너는 필요한 것만 override | `NAVI_ROUTE`(조회), `NAVI_ROUTE_SEARCH`(requestRoute·cancelRoute) | ETA |
| Light (`getLight`) | `checkCabinLightSwitchCapabilities(onComplete: (List<LightSwitch>))`, `getCabinLightSwitch(onComplete: (LightSwitch?))`, `setCabinLightSwitch(LightSwitch, onComplete: (LightSwitch?), onFailed)` | CAR_LIGHTS / CONTROL_CAR_LIGHTS | 실내등 차 찾기 |

### spike에서만 사용한 API

| 도메인 | API | 퍼미션 | 현재 구분 |
|---|---|---|---|
| Seat (`getSeat`) | `getCurrentSeatOccupancy`, `registerSeatOccupancy`, `registerSeatBeltBuckledState` | CAR_SEATS | 착석·벨트 검증 로그. 기본 런타임·탑승 판정에서 사용 안 함 |
| Light (`getLight`) | `setAmbientLightSwitch`, `setAmbientLightBrightnessColor`, `checkAmbientLightBrightnessColorCapabilities` | CAR_LIGHTS + CONTROL_CAR_LIGHTS | 앰비언트 제어 검증 기록 |
| HVAC (`getHVAC`) | `getHvacTemperature`, `setHvacTemperature`, `setHvacFanSpeed`, `setHvacRecirculation` | CAR_CLIMATE / CONTROL_CAR_CLIMATE | 공조 제어 검증 기록 |

### 주요 타입 (import 경로)

| 타입 | 패키지 | 값 |
|---|---|---|
| `Vehicle` | `ai.pleos.playground.vehicle` | |
| `SeatArea`, `DoorArea`, `WindowArea` | `…vehicle.api.model.area` | |
| `SeatValue` | `…vehicle.constant.area` | ROW_1_LEFT, ROW_1_CENTER, ROW_1_RIGHT, ROW_2_*, ROW_3_* |
| `DoorValue` | `…vehicle.constant.area` | ROW_1_LEFT/RIGHT, ROW_2_LEFT/RIGHT, ROW_3_LEFT/RIGHT, HOOD, REAR |
| `VehicleGear` | `…vehicle.constant.control` | GEAR_PARK 등 |
| `LightSwitch` | `…vehicle.constant.control` | OFF, ON, DAYTIME_RUNNING, AUTOMATIC |
| `TemperatureUnit` | `…vehicle.constant.unit` | CELSIUS, FAHRENHEIT |
| `GearSelectionListener`, `DoorPositionListener`, `SeatOccupancyListener`, `SeatBeltBuckledStateListener` | `…vehicle.listener` | 모두 `onFailed(Exception)` 포함 |
| `AreaBounds`, `CapabilityConfig` | `…vehicle.api.model.capability` | |

### 과거 spike의 제약 기록

- `Safety.getTelltalesInfo` (경고등): `PRIVATE_CAR_SAFETY`, signature|privileged
- 당시 Playground 신청 화면에는 Vehicle Control·FusedLocation·TextToSpeech·SpeechToText가 "준비 중"으로 표시됐다. 현재 앱은 Vehicle·TTS·STT를 의존성으로 포함하고 사용한다. 신청 화면 표기와 런타임 구현을 구분한다.
- 트립 주행시간·거리: 에뮬레이터에서 `ERROR_NOT_SUPPORTED_PROPERTY`

## 6. 현재 위치·음성·ETA 구현

| 기능 | 현재 구현 | 검증·적용 범위 |
|---|---|---|
| 음성 출력 | **Pleos TextToSpeech SDK 3.3.1, `Mode.ON_DEVICE`**. 초기화 전 요청은 큐에 보관하고 초기화 후 읽음 | 11/07 초기화·발화 완료 콜백 기록. Android 기본 TTS 초기화 실패(-1)는 과거 비교 기록 |
| 음성 인식 | **Pleos SpeechToText SDK 3.3.1, `Mode.ON_DEVICE`**. 한국어 설정 후 `request()`, 완료된 `onUpdated(stt, completed)`를 반환 | 11/07 `onReady` 기록까지만 있음. 실제 발화 승인·거절은 사용자 확인 대기(A6) |
| 차 위치 | **LocationManager 유지**. `GPS_PROVIDER`, 마지막 위치 조회 + 3초·최소 이동 0m 구독 | 11/07 `emu geo fix` 수신 기록. Pleos FusedLocation 의존성·교체 로직 없음 |
| ETA | NaviHelper 값이 있으면 사용, 없으면 직선거리 추정 | `source: navi` / `estimate`. 양쪽 위치도 내비 값도 없으면 null |
| 요청 수신 | WebSocket(OkHttp, 마중 서버) | Application 범위 연결과 포그라운드 서비스 구현. 프로세스 미실행 시 수신은 검증 대기 |
| 차 공유 화면 지도 | OSM MAPNIK 타일 + osmdroid `6.1.20` | 차·탑승자 마커, 50m 원, 자동 범위 조정 구현 완료(A9). CAR_APP.md 4절 |
| 폰 위치·공유 화면 | Sharing 전용 LocationManager GPS·위치 FGS·osmdroid `6.1.20`, manager의 3초 마지막 fix heartbeat | P4 debug 범위 main QA 완료. 실제 지도·권한·정지 heartbeat·이동·탑승·알림/최근 앱 종료 근거와 검증 경계는 PHONE_APP.md 8절 |

`VoiceIO`, `LocationSource`는 인터페이스다. `MaejungApp`이 현재 구현체 `PleosVoiceIO`·`AndroidLocationSource`를 주입하며, SDK 공개 여부에 따른 자동 교체는 하지 않는다.

- 과거 조사에서는 공개 Maven(nexus-playground.pleos.ai)의 NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1, FusedLocation 1.1.3, LLM 2.1.3.2를 기록했다. 현재 차 앱 의존성에 있는 것은 Vehicle 2.3.2·NaviHelper 3.0.5·TextToSpeech 3.3.1·SpeechToText 3.3.1이다.
- **Pleos TTS 사용법**: `TextToSpeech(applicationContext, Mode.ON_DEVICE)` → `initialize(OnInitializeListener)` → `setLocale(Locale.KOREA)` → `addEventListener(EventListener)` → `speak(text)`. 퍼미션 `pleos.car.permission.TTS_SERVICE`. ON_DEVICE는 앱 등록(Client ID/Secret)이 필요 없다. **`onReady()`는 발화가 끝날 때마다 다시 오므로 그 안에서 `speak()`를 부르면 무한 반복된다.** import는 `ai.pleos.playground.tts.{TextToSpeech, constant.Mode, constant.ErrorType, listener.OnInitializeListener, listener.EventListener}`다.
- **Pleos STT 대기 규칙**: 초기화 최대 5초, 안내 TTS 완료 최대 8초를 기다린 뒤 300ms 후 `request()`한다. 인식은 최대 10초 기다리고 완료 결과가 없으면 `stop()`한다. 오류·결과 없는 종료·시간 초과는 null이며 버튼으로도 응답할 수 있다. `VoiceIO.kt`의 SpeechRecognizer 설명 주석은 과거 내용이고 실제 구현은 `PleosVoiceIO`다.

## 7. 에뮬레이터 스파이크 결과 (Pleos Connect Emulator v2.1.0)

아래는 과거 검증 이력이며 11/07 항목은 다른 에이전트가 남긴 기록이다. 이번 문서 갱신에서는 에뮬레이터를 실행하지 않았다. 공조·앰비언트·착석·벨트 성공 기록이 기본 앱의 사용을 뜻하지는 않는다.

| 날짜 | 항목 | 결과 |
|---|---|---|
| 11/03 | 퍼미션(조회·제어) | 설치 시 모두 granted |
| 11/03 | 제어: 설정온도, 내기순환 off, 앰비언트 ON + 색 | 성공. 앰비언트는 CONTROL_CAR_LIGHTS 추가 + 지원 영역으로 호출 |
| 11/03 | 구독: 브레이크 | 성공 |
| 11/03 | 트립 주행시간·거리 | 미지원 |
| 11/06 | 외부 통신: HTTPS GET (httpbin.org) | 200 |
| 11/06 | 외부 통신: WebSocket (echo.websocket.org) | 연결, 송수신 성공 |
| 11/06 | GMS(구글 서비스) | 없음 → FCM 불가 |
| 11/06 | 기어 구독 | 등록 즉시 `GEAR_PARK` |
| 11/06 | 문 위치 | 캐퍼빌리티 6개 영역(승객 문 0~1, HOOD·REAR 0~100). Virtual Console BODY 패널 RR 클릭 → `ROW_2_RIGHT = 1`, 다시 클릭 → `0` |
| 11/06 | 착석·벨트 조회·구독 | 성공. 기본값은 승객석 착석 true, 벨트 false |
| 11/06 | 실내등 켜기·끄기 | 성공. set 후 `getCabinLightSwitch`, `getCurrentCabinLightState` 조회값도 ON/OFF로 바뀜 |
| 11/06 | 비상등 | SDK에 API 없음 |
| 11/07 | 헤드업 알림 | 성공. 내비 화면이 앞에 있을 때 `IMPORTANCE_HIGH` 알림이 화면 위쪽에 "수락/거절" 버튼과 함께 뜨고, 두 버튼 모두 앱 BroadcastReceiver로 전달됨. `CATEGORY_CALL`은 전화 수신 모양(아이콘 버튼, 화면 왼쪽 아래)으로 바뀐다. 헤드업은 6~8초 뒤 알림 센터로 들어간다. 같은 id 알림이 남아 있으면 다시 헤드업으로 뜨지 않는다. 화면: `docs/pickup-alert-emulator.png` |
| 11/07 | C1 차 위치 주입 | 성공. `adb emu geo fix 127.06030 37.49950` → `LocationManager` GPS 구독에 바로 들어옴. 정차 중에도 3초마다 같은 좌표가 계속 옴(heartbeat에 유리). 제공자 `[passive, fused, gps]` |
| 11/07 | 런타임 퍼미션 부여 | 차 에뮬레이터 앱은 사용자 10에서 돈다. `adb shell pm grant --user 10 com.maejung <퍼미션>`이어야 한다(`--user` 없이는 적용 안 됨) |
| 11/07 | V1 안드로이드 TTS | 실패. `TextToSpeech` 초기화 status -1(엔진 없음) |
| 11/07 | V1 Pleos TTS ON_DEVICE | 성공. 초기화 → `onStart` → 약 2.6초 뒤 `onDone`(한국어 문장) |
| 11/07 | V1 STT | `SpeechRecognizer.isRecognitionAvailable` = true(인식 서비스 `ai.umos.ambientai`)지만, 실제로 `startListening`하면 준비 콜백도 오류도 없이 10초 시간 초과(앱이 뒤에 있을 때는 오류 9, 마이크 타입 포그라운드 서비스로 해결). **Pleos STT ON_DEVICE로 바꾸자 `onReady`("말할 준비 됨")까지 옴.** 실제 인식은 사람 발화로 확인 필요 |
| 11/07 | N1 NaviHelper | 성공. `requestRoute(RouteInfo(lat, lng, …))` → `onRouteStarted(RouteStartInfo(duration=1620, distance=12000, …))`, `onDrivingInfo(destination.distance=12000m, duration=1620초)`. `cancelRoute()` → `onRouteCancelled`. **`requestRoute`를 부르면 내비 앱이 화면 앞으로 나온다.** 에뮬레이터 내비는 `emu geo fix`를 쓰지 않고 자체 기본 위치(성남 시흥동 부근)에서 경로를 잡는다(NMEA 체크섬 경고) |

- Virtual Console(`adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`)의 BODY 패널에서 문 열림·닫힘, 다른 패널에서 기어, 공조, 앰비언트를 주입할 수 있다. 착석(Seat Occupancy) 카드도 있다.

## 8. 구현·검증 현황

- 구현 완료: 차 앱 상태 머신·알림·위치 공유·실내등 저장/복구·500m 길안내·ETA fallback·OSM 지도(A9). NaviHelper와 GPS 주입은 7절의 에뮬레이터 기록이 있다.
- 이전 세션 검증: JBR 21, offline, `--rerun-tasks`로 app 46 + protocol 9 + server 34 = 총 89개 테스트 실패 0개, debug assemble 성공. 이번 문서 갱신에서 새로 실행한 결과는 아니다.
- 기존 P2 메인의 회귀 검증 이력(UI 재설계 전): JBR 21·`--rerun-tasks --offline --no-daemon --console=plain`으로 protocol·app 테스트와 차 debug 조립 성공(12초·47개 태스크 실행). app 46 + protocol 9 = 55개, 실패·오류·건너뜀 0. 서버 소스는 그대로여서 재실행하지 않았고 34개는 이전 결과다. 별도 phone 16개 테스트·debug 조립 성공, 폰 lint 오류 0·경고 8개로 성공했다. UI·입력·회전·백그라운드 프로세스 재생성 검증은 PHONE_APP.md 6절이다.
- UI 재설계 최종 빌드(메인 제공): `:phone:testDebugUnitTest :phone:assembleDebug :phone:lintDebug` 오프라인 실행 성공(19초·55개 태스크: 실행 14·최신 상태 41), phone 24개 전부 재실행·통과(기존 16 + 새 나침반/ETA 8, 실패·오류·건너뜀 0), lint 오류 0·기존 경고 8개, 최종 debug APK 설치 Success. API 27 테마 오류는 기본 리소스·`values-v27` 분리로 수정했다.
- UI 재설계 네이티브 QA·독립 검토 PASS(메인·리뷰어 Zeno 제공): 320dp/글꼴 1.3의 차 라벨·HUD 여백, 포커스 중 코드/커서, 스크롤 종료 버튼 접근을 확인해 필수 지적 2건을 해결했다. `Minji`·`012345` 입력 후 홈 인사, 정보 없음의 마커·측정값 숨김, 종료 확인의 계속 보기와 직접 종료, 가로 근접 화면·종료 버튼 스크롤도 확인했다. PNG 경로와 검증 경계는 PHONE_APP.md 6절이다. API 26 런타임은 미검증이다.
- 음성(A6): Pleos ON_DEVICE TTS/STT 구현 완료. STT 실제 사람 발화와 승인·거절 전이는 사용자 검증 대기다.
- 서버 Docker(S4): 설정 작성, Docker Hub timeout으로 이미지 빌드 성공 미확인. Railway(S5): 배포 미진행.
- 폰 앱의 이전 이력: P1은 기존 `Medium_Phone_API_36.0`(API 36 Play Store arm64)의 차·폰 동시 부팅으로 완료했다(차 `emulator-5554`, 폰 `emulator-5556`, 폰 boot complete=1, 이미지 다운로드 없음). P2 모듈·오프라인 UI·종료 6가지와 승인 UI 재설계의 debug 폰 AVD 결과도 이전 이력으로 유지한다. 당시 release 미리보기 차단은 소스·단위 테스트 근거이며 release APK 런타임 검증은 아니다.
- 폰 P3 완료: 기존 통신 계약으로 REST 페어링·자격 보관·WebSocket·세션 상태 전이·재연결을 구현했다. 메인 제공 응답 기한 수정 후 phone 111개(매니저 42개 포함)와 직전 새 실행의 변경 없는 app 50개·protocol 9개를 합해 170개 통과, 실패·오류·건너뜀 0이다. 폰 테스트·debug 조립·lint 재실행은 23초·55개 태스크 실행으로 성공했고 lint는 오류 0·경고 10개다. 실제 페어링·요청·수락·양쪽 직접 종료·거절·requested/sharing 복원, 4409 재접속 중단과 명시적 재연결, 서버 재시작 후 자동 동기화, 4401 자격 삭제, 미리보기 격리·재실행 시 LIVE 진입을 확인했다. 예외 검증 후 최신 APK의 전체 정상 흐름도 재확인했으며 소스·테스트·Manifest의 독립 검토는 승인됐다. 범위별 근거는 [PHONE_APP.md 7절](PHONE_APP.md#7-p3-검증-결과)에 있다. P4 GPS·위치 서비스·양쪽 실제 지도, release APK 런타임·API 26 설치/런타임은 검증에 포함하지 않는다.
- 폰 P4 완료: main 제공 `green-notices` 전체 실행은 phone 184 + app 50 + protocol 9 = 243개 통과(실패·오류·건너뜀 0), 29초·81개 태스크 실행·lint 오류 0/경고 12개다. 마지막 상수 정리 후 증분 실행은 27초·실행 15/최신 상태 66이며 phone 184개를 재실행하고 app/protocol은 직전 전체 실행 결과를 유지했다. 최신 APK 설치·권한/마커·coarse 지연 후 실제 fix·양쪽 공유·알림 종료와 app_closed 서버 종료까지 main이 signoff했다. 37개 변경 파일·80개 보호 파일 일치와 범위별 근거는 [PHONE_APP.md 8절](PHONE_APP.md#8-p4-검증-결과)에 기록한다. P1/P2/P3 결과는 역사로 보존한다.
- P4 검증 경계: 실물 기기·API 26 설치/런타임·release 런타임은 미검증이다. coarse GPS 지연·force-stop·위치 FGS 없는 상태의 콜백 한계는 PHONE_APP.md 5·8절을 따른다. 보호된 차 앱의 두 점이 0m로 겹칠 때 세계 범위로 확대되는 기존 현상은 범위 밖으로 남겼다.
- 외부 환경 검증 대기: 실제 마켓의 외부 통신·백그라운드 연결 정책, 앱 프로세스가 실행되지 않은 상태의 요청 수신. 현재 포그라운드 서비스 구현은 프로세스가 꺼져도 수신된다는 보장이 아니다.

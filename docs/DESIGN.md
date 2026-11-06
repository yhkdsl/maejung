# 마중 설계

기획 원본(시나리오, 사업성, 개인정보 원칙)은 Obsidian `01. Projects/HMG 차량용 앱 공모전/HMG 차량용 앱 공모전 - 마중 기획서 초안.md`에 있다. 이 문서는 코드와 같이 바뀌는 설계만 다룬다.

## 0. 설계 원칙

- **양쪽 동의로 시작하고, 어느 쪽이든 끝낼 수 있다.** 탑승자가 요청하고 운전자가 승인해야 위치가 오간다.
- **위치는 세션 동안만 오간다.** 서버는 위치를 메모리에만 두고, 세션이 끝나면 지운다. 디스크·로그에 남기지 않는다. 상시 추적 기능은 만들지 않는다.
- **종료는 자동이 기본, 수동은 예비다.** 탑승 감지, 무응답 2분, 세션 60분 중 먼저 오는 조건으로 끝낸다.
- **차량 제어는 실내등 하나만 쓴다.** 정차 중에만 켜고, 끝나면 켜기 전 상태로 되돌린다. 기어·문·좌석은 읽기만 한다. 대기 모드(화면·공조 절약)와 약속 색 앰비언트는 빼기로 했다(2026-11-06).
- 주행 중에는 조작을 최소화한다. 승인은 음성 또는 큰 버튼 하나.

## 1. 구성

```
[폰 앱] ──HTTPS/WebSocket──► [마중 서버: Spring Boot, 메모리 세션] ◄──WebSocket── [차 앱 (Pleos)]
 요청, 내 위치                  연결 코드, 세션, 위치 중계, 종료 시 삭제     승인, 차 위치, ETA, 실내등, 탑승 판정
```

- 차에는 GMS가 없어 FCM을 못 쓴다. 차 앱은 서버 WebSocket에 붙어 요청을 받는다.
- 폰 앱은 Android만 만든다. 위치는 FusedLocationProvider(폰에는 GMS가 있다).
- 상세: 통신 [PROTOCOL.md](PROTOCOL.md), 서버 [SERVER.md](SERVER.md), 차 앱 [CAR_APP.md](CAR_APP.md), 폰 앱 [PHONE_APP.md](PHONE_APP.md).

## 2. 세션 흐름 (개요)

정확한 전이 규칙은 차 앱 [CAR_APP.md](CAR_APP.md) 3절, 폰 앱 [PHONE_APP.md](PHONE_APP.md) 4절, 서버 [PROTOCOL.md](PROTOCOL.md) 4절이 기준이다. 이 표는 개요다.

| 단계 | 서버 상태 | 차 앱 상태 | 차 앱 | 폰 앱 |
|---|---|---|---|---|
| 요청 | REQUESTED | Requested | 알림 + 음성, 승인/거절 | "엄마 차에 알렸어요" |
| 공유 | SHARING | Sharing | 상대 위치, ETA | 차 위치, ETA |
| 근접 | SHARING | Nearby (50m 이내 + 기어 P) | "○○가 가까이 왔어요", 실내등 ON | "실내등 켜진 차가 엄마 차예요" |
| 종료 | ENDED | Ended(reason) | 실내등 복구, 이유별 안내 | 이유별 안내 |

- 종료 이유는 하나의 Ended 상태 안에서 `reason`으로 구분한다: `boarded`, `manual`, `rejected`, `request_timeout`, `peer_lost`, `max_duration`(PROTOCOL.md 4절).
- Nearby에서 거리가 70m 넘게 벌어지거나 기어가 P가 아니게 되면 Sharing으로 돌아가고 실내등을 복구한다(50/70m 이중 기준으로 경계에서 깜빡임 방지).
- 종료되면 서버는 세션과 마지막 위치를 메모리에서 지운다.

## 3. 탑승 판정

Nearby 상태에서 아래가 모두 맞으면 Ended(boarded).

1. 기어 `GEAR_PARK`
2. 승객 문(`ROW_1_RIGHT`, `ROW_2_LEFT`, `ROW_2_RIGHT`) 중 하나가 열렸다가(1) 닫힘(0)
3. 마지막 탑승자 위치와 차 위치가 30m 이내 (GPS 오차를 고려한 초기값)

- 착석(`SeatOccupancy`)·벨트(`SeatBeltBuckledState`)는 보조 신호로 로그만 남긴다. 에뮬레이터 기본값이 모든 좌석 착석 true라 판정 조건으로 쓰기 어렵다.
- 판정 직후 "○○ 탑승했나요?"를 묻지 않고 바로 끝낸다. 잘못 끝났으면 폰에서 다시 요청하면 된다.

## 4. 차량 제어: 실내등 차 찾기 (2026-11-06 확정)

- Nearby에 들어갈 때 실내등의 현재 값을 저장하고 켠다. Ended나 Sharing으로 나갈 때 저장한 값으로 되돌린다. 원래 켜져 있었으면 켜진 채로 둔다.
- 현재 값(`LightSwitch`: OFF, ON, DAYTIME_RUNNING, AUTOMATIC)과 지원 여부는 앱 시작 때와 Sharing 진입 때 `checkCabinLightSwitchCapabilities` + `getCabinLightSwitch`로 읽는다. 원래 값은 enum 그대로 저장하고, Boolean으로 줄이지 않는다. 저장·복구 판단은 차 앱 상태 머신이 한다(CAR_APP.md 3절).
- `registerCabinLightState(LightStateListener)`도 있지만 실제 점등 상태(`LightState`)용이다. 스위치 설정값 구독 API는 문서에 없어서 쓰지 않는다.
- 지원 값에 ON이 없으면 실내등을 건너뛰고 음성·폰 안내만 한다.
- 비상등은 Light API에 get·set이 모두 없다(Virtual Console에만 Hazard Lights 카드가 있다). 그래서 실내등을 쓴다.
- 하향등·상향등·안개등·미등 set도 있지만, 정차 중 사람을 향해 켜면 눈부시고 위험해서 쓰지 않는다.
- 실내등은 낮에는 밖에서 잘 안 보인다. 주 사용 장면(저녁~밤 하원)에 맞춘 기능이다.

### 쓰지 않는 제어 (검증 기록)

대기 모드(화면·공조 절약)와 약속 색 앰비언트는 빼기로 했다. 나중을 위해 검증 내용만 남긴다.

- 모든 제어 전에 `checkXxxCapabilities`로 지원 여부와 범위를 확인한다.
- **영역은 캐퍼빌리티가 돌려준 값을 그대로 쓴다.** 앰비언트 라이트와 내기순환은 좌석별이 아니라 차량 전체 영역이라 `SeatArea(ROW_1_LEFT)`로 호출하면 `area ID: 0x1 not supported`가 난다.
- 앰비언트 set에는 `CONTROL_CAR_LIGHTS`가 필요하다(문서에는 CAR_LIGHTS만 적혀 있지만 없으면 SecurityException).

## 5. Vehicle SDK 매핑

`ai.pleos.playground:Vehicle:2.3.2`. 진입점 `Vehicle(context)` → `initialize()` → `getXxx()` → 사용 → `release()`. 모든 호출은 비동기 `onComplete`/`onFailed`.

### 사용하는 API

| 도메인 (getter) | API | 퍼미션 | 용도 |
|---|---|---|---|
| Gear (`getGear`) | `registerGearSelection(GearSelectionListener)` → `onSelected(VehicleGear?)` | CAR_DRIVE | 탑승 판정 |
| Door (`getDoor`) | `checkDoorPositionCapabilities(onComplete: (Map<DoorArea, AreaBounds<Int>>))`, `getCurrentDoorPosition(DoorArea, (Int?))`, `registerDoorPosition(DoorArea, DoorPositionListener)` → `onPositionUpdated(Int?)` | CAR_DOORS (normal) | 탑승 판정 |
| Seat (`getSeat`) | `getCurrentSeatOccupancy(SeatArea, (Boolean?))`, `registerSeatOccupancy(SeatArea, SeatOccupancyListener)`, `registerSeatBeltBuckledState(SeatArea, SeatBeltBuckledStateListener)` | CAR_SEATS (normal) | 탑승 보조 신호 |
| Light (`getLight`) | `checkCabinLightSwitchCapabilities(onComplete: (List<LightSwitch>))`, `getCabinLightSwitch(onComplete: (LightSwitch?))`, `setCabinLightSwitch(LightSwitch, onComplete: (LightSwitch?), onFailed)` | CAR_LIGHTS / CONTROL_CAR_LIGHTS | 실내등 차 찾기 |
| Light (`getLight`) | (검증만, 사용 안 함) `setAmbientLightSwitch(area, LightSwitch, …)`, `setAmbientLightBrightnessColor(area, argb, …)`, `checkAmbientLightBrightnessColorCapabilities` | CAR_LIGHTS + CONTROL_CAR_LIGHTS | - |
| HVAC (`getHVAC`) | (검증만, 사용 안 함) `getHvacTemperature`, `setHvacTemperature(area, Float, TemperatureUnit, …)`, `setHvacFanSpeed(area, Int, …)`, `setHvacRecirculation(area, Boolean, …)` | CAR_CLIMATE / CONTROL_CAR_CLIMATE | - |

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

### 쓸 수 없는 것

- `Safety.getTelltalesInfo` (경고등): `PRIVATE_CAR_SAFETY`, signature|privileged
- Playground "준비 중": Vehicle Control, FusedLocation, TextToSpeech, SpeechToText. 제어는 에뮬레이터에서 동작한다(7절).
- 트립 주행시간·거리: 에뮬레이터에서 `ERROR_NOT_SUPPORTED_PROPERTY`

## 6. 대체 구현 (SDK 미오픈 대응)

| 기능 | 지금 | Pleos SDK 오픈 후 |
|---|---|---|
| 음성 출력 | `android.speech.tts.TextToSpeech` | TextToSpeech SDK |
| 음성 인식 | `android.speech.SpeechRecognizer` | SpeechToText SDK |
| 차 위치 | `LocationManager` | FusedLocation SDK |
| 요청 수신 | WebSocket(OkHttp, 마중 서버) | 변경 없음 (차에 GMS 없음) |

`VoiceIO`, `LocationSource`를 인터페이스로 두고 구현체만 바꾼다.

## 7. 에뮬레이터 스파이크 결과 (Pleos Connect Emulator v2.1.0)

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

- Virtual Console(`adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`)의 BODY 패널에서 문 열림·닫힘, 다른 패널에서 기어, 공조, 앰비언트를 주입할 수 있다. 착석(Seat Occupancy) 카드도 있다.

## 8. 미결 사항

- [ ] NaviHelper로 임의 좌표까지 ETA 조회·목적지 설정
- [ ] 실제 마켓에서 외부 통신·백그라운드 WebSocket 유지 정책
- [ ] 차 앱이 꺼져 있을 때 요청 수신(포그라운드 서비스 허용 여부)
- [ ] 에뮬레이터 가짜 GPS(`adb emu geo fix`)가 Pleos 에뮬레이터 `LocationManager`에 들어오는지

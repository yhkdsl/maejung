# 동행 설계

기획 원본(시나리오, 사업성, 안전 원칙)은 Obsidian `01. Projects/HMG 차량용 앱 공모전/`에 있다. 이 문서는 코드와 같이 바뀌는 설계만 다룬다.

## 0. 설계 원칙

- **핵심 기능은 음성으로 완결한다.** 동화, 도슨트, 졸음 대화·휴게소 안내는 차량 제어 없이 동작해야 한다.
- **차량 제어는 연출 레이어다.** Light(앰비언트)와 HVAC(온도·외기·풍량)만 쓴다. 지원되지 않거나 실패하면 연출만 건너뛴다.
- **창문·도어 등 주행 안전에 영향을 주는 제어는 하지 않는다** (2026-11-03 결정).
- 연출이 끝나면 연출 전 상태로 되돌린다.

## 1. 모드와 우선순위

| 우선순위 | 모드 | 진입 조건 | 종료 조건 |
|---|---|---|---|
| 1 | AWAKE (각성) | 피로 단계 2 이상 | 피로 단계 1 이하로 떨어지거나 휴게소 도착 |
| 2 | KIDS_STORY (키즈 스토리) | 출발 시 "아이 동승" 선택 + 목적지 설정 | 도착, 아이 잠듦 선언, 사용자 중지 |
| 3 | DOCENT (도슨트) | 어른만 탑승, 또는 키즈 모드에서 아이 잠듦 | 도착, 사용자 중지 |
| - | REST (휴식, 확장) | 정차 + 최근 AWAKE 이력 | 출발(기어 D 또는 주행 시작) |
| - | IDLE | 위 조건 없음 | - |

- 상위 모드가 켜지면 하위 모드는 **일시정지**하고, 상위 모드가 끝나면 이어서 재생한다(동화 2부 이어 듣기).
- 피로 단계 1(주의)은 모드를 바꾸지 않고, 진행 중인 콘텐츠 사이에 가벼운 대화를 끼워 넣는다.

```
           피로≥2                    피로≤1
KIDS_STORY ───────► AWAKE ─────────────────► (이전 모드 재개)
DOCENT     ───────►   │  휴게소 도착
                      └────────────► REST ──출발──► (이전 모드 재개)
KIDS_STORY ──아이 잠듦──► DOCENT
```

## 2. 컴포넌트

```
[Vehicle/Navi/Location 신호] ─► ContextEngine ─► FatigueScorer
                                     │                │
                                     ▼                ▼
                              ModeOrchestrator (우선순위: 각성 > 키즈 > 도슨트)
                                     │
                ┌────────────────────┼────────────────────┐
                ▼                    ▼                    ▼
          StoryEngine          AwakeController       DocentEngine
     (ETA 맞춤 이야기, 분기)   (퀴즈, 휴게소 제안)     (POI 트리거)
                │                    │                    │
                └──────────► AmbientDirector ◄────────────┘
                         (Light/HVAC/Window 연출 큐)
                                     │
                               VoiceIO (STT/TTS)
```

| 컴포넌트 | 책임 |
|---|---|
| ContextEngine | Vehicle·NaviHelper·위치 신호를 구독해 하나의 `DriveContext` 스냅샷으로 합친다 |
| FatigueScorer | `DriveContext`와 음성 응답 지표로 피로 점수·단계를 계산한다 |
| ModeOrchestrator | 우선순위 규칙으로 모드를 전환하고, 일시정지·재개를 관리한다 |
| StoryEngine | 백엔드에 이야기 생성을 요청하고 ETA에 맞춰 분량을 조절한다. 다음 장면을 미리 받아 둔다 |
| AwakeController | 각성 대화(퀴즈, 끝말잇기), 휴게소 경유지 제안 |
| DocentEngine | 위치 기반으로 해설 트리거, 경유지 추가 제안 |
| AmbientDirector | 연출 태그를 Light·HVAC 호출로 바꾼다. capability 확인과 원상 복구를 맡는다 |
| VoiceIO | TTS·STT 추상화. 지금은 Android 표준 API, Pleos SDK가 열리면 교체 |
| Backend (별도) | LLM 프록시. API 키 보관, 아동 콘텐츠 필터, 캐싱 |

## 3. 피로 점수

진단이 아니라 휴식 권유다. 최종 판단은 운전자가 한다.

| 신호 | 출처 | 가중 |
|---|---|---|
| 연속 주행시간(2시간 이후 급증) | 앱 자체 주행 타이머(주행 시작~정차 구간 누적). `Odometer.getTripCurrentDrivingTime`은 에뮬레이터에서 `ERROR_NOT_SUPPORTED_PROPERTY`라 지원될 때만 보조로 쓴다. 데모는 시나리오 리플레이어로 시간을 주입한다 | 높음 |
| 시간대(02~06시, 14~16시) | 시스템 시각 | 중간 |
| 크루즈 장시간 사용(단조 주행) | `DrivingMode.getCruiseControlState` | 중간 |
| 브레이크 패턴 변화(평소 대비 빈도 급변) | `Brake.registerBrakePedalStatus` | 중간 |
| 음성 퀴즈 응답 지연·오답 | VoiceIO | 높음 |

| 단계 | 의미 | 개입 |
|---|---|---|
| 0 | 정상 | 없음 |
| 1 | 주의 | 콘텐츠 사이에 가벼운 대화 |
| 2 | 경고 | AWAKE 모드 진입, 환경 각성 연출 |
| 3 | 강한 권유 | 다음 휴게소·졸음쉼터 경유지 추가 제안 |

가중치와 임계값은 에뮬레이터 시나리오로 조정한다. 초기값은 코드 상수로 두고 여기에 기록한다.

## 4. 연출 태그

이야기와 각성 스크립트에 태그를 넣으면 AmbientDirector가 실행한다.

```
[light:ocean] 부기는 파란 바다로 첨벙 뛰어들었어요. [hvac:cool]
```

| 태그 | 동작 | API |
|---|---|---|
| `light:<preset>` | 앰비언트 색 변경(ocean=#005AFF, forest, sunset, awake=#00FFC8 …) | `Light.setAmbientLightSwitch(ON)` + `setAmbientLightBrightnessColor(area, argb)` |
| `hvac:warm` / `hvac:cool` | 설정온도 ±2~3°C | `HVAC.setHvacTemperature(area, °C, CELSIUS)` |
| `hvac:fresh` | 외기 유입 | `HVAC.setHvacRecirculation(area, false)` |
| `hvac:fan+` | 풍량 증가 | `HVAC.setHvacFanSpeed(area, level)` (범위는 capability로 확인) |

- 모든 제어 전에 `checkXxxCapabilities`로 지원 여부와 범위를 확인한다. 미지원이면 태그를 건너뛰고 음성만 재생한다.
- **영역은 캐퍼빌리티가 돌려준 값을 그대로 쓴다.** 앰비언트 라이트와 내기순환은 좌석별이 아니라 차량 전체 영역이라 `SeatArea(ROW_1_LEFT)`로 호출하면 `area ID: 0x1 not supported`가 난다. 내기순환 지원 영역은 `[ROW_1_LEFT, ROW_1_RIGHT, ROW_2_LEFT, ROW_2_CENTER, ROW_2_RIGHT]` 하나다.
- 창문 제어는 쓰지 않는다. 참고로 창문 위치 범위는 0~10(10 = 완전 개방)이다.
- 모드가 끝나면 연출 전 상태로 복구한다(시작 시 현재 값을 저장).

## 5. Vehicle SDK 매핑

`ai.pleos.playground:Vehicle:2.3.2`. 진입점 `Vehicle(context)` → `initialize()` → `getXxx()` → 사용 → `release()`. 모든 호출은 비동기 `onComplete`/`onFailed`.

### 사용하는 API

| 도메인 (getter) | API | 퍼미션 | 용도 |
|---|---|---|---|
| Odometer (`getOdometer`) | `getTripCurrentDrivingTime(onComplete: (Int?) )` 분 / `registerTripCurrentDrivingTime(DrivingTimeListener)` | CAR_DRIVE | 피로 점수, 이야기 진행 |
| Odometer | `getTripCurrentDrivingDistance(onComplete: (Float?))` m | CAR_DRIVE | 진행률 |
| Brake (`getBrake`) | `getCurrentBrakePedalStatus` / `registerBrakePedalStatus(BrakePedalStatusListener)` | CAR_DRIVE | 브레이크 패턴 |
| DrivingMode (`getDrivingMode`) | `getCruiseControlState(onComplete: (CruiseControl?))` | CAR_DRIVE | 단조 주행 |
| HVAC (`getHVAC`) | `getHvacTemperature(area, unit, …)`, `setHvacTemperature(area, Float, TemperatureUnit, …)` | CAR_CLIMATE / CONTROL_CAR_CLIMATE | 연출·각성 |
| HVAC | `setHvacRecirculation(area, Boolean, …)`, `setHvacFanSpeed(area, Int, …)` | CONTROL_CAR_CLIMATE | 외기 유입, 풍량 |
| Light (`getLight`) | `setAmbientLightSwitch(area, LightSwitch, …)`, `setAmbientLightBrightnessColor(area, argb, …)`, `checkAmbientLightBrightnessColorCapabilities(onComplete: (CapabilityConfig, List<SeatArea>))` | CAR_LIGHTS + **CONTROL_CAR_LIGHTS** (문서에는 CAR_LIGHTS만 적혀 있지만 set에 제어 퍼미션이 없으면 SecurityException) | 장면 색 |
| HVAC | `checkHvacRecirculationCapabilities(onComplete: (CapabilityConfig, List<SeatArea>))` | CAR_CLIMATE | 내기순환 지원 영역 |
| Window (`getWindow`) | (사용 안 함, 스파이크에서만 검증) `setWindowPosition(WindowArea, Int, …)` | CAR_WINDOWS / CONTROL_CAR_WINDOWS | - |
| Seat, EvBattery | (확장) 휴식 모드 리클라인, 휴식 가능 시간 | CAR_SEATS, CAR_ENERGY | REST 모드 |

### 주요 타입 (import 경로)

| 타입 | 패키지 | 값 |
|---|---|---|
| `Vehicle` | `ai.pleos.playground.vehicle` | |
| `SeatArea(vararg SeatValue)`, `WindowArea(vararg WindowValue)` | `…vehicle.api.model.area` | |
| `SeatValue` | `…vehicle.constant.area` | ROW_1_LEFT, ROW_1_CENTER, ROW_1_RIGHT, ROW_2_*, ROW_3_* |
| `WindowValue` | `…vehicle.constant.area` | FRONT_WINDSHIELD, REAR_WINDSHIELD, ROW_1_LEFT/RIGHT, ROW_2_LEFT/RIGHT, ROOF_TOP_1/2 |
| `TemperatureUnit` | `…vehicle.constant.unit` | CELSIUS, FAHRENHEIT |
| `LightSwitch` | `…vehicle.constant.control` | OFF, ON, DAYTIME_RUNNING, AUTOMATIC |
| `CruiseControl` | `…vehicle.constant.control` | OTHER, ENABLED, ACTIVATED, USER_OVERRIDE, SUSPENDED, FORCED_DEACTIVATION_WARNING |
| `DrivingTimeListener.onDrivingTimeUpdated(Int?)`, `BrakePedalStatusListener.onBrakePedalStatusUpdated(Boolean?)` | `…vehicle.listener` | 둘 다 `onFailed(Exception)` 포함 |
| `AreaBounds`, `CapabilityConfig` | `…vehicle.api.model.capability` | |

### 쓸 수 없는 것

- `Safety.getTelltalesInfo` (경고등): `PRIVATE_CAR_SAFETY`, signature|privileged
- Playground "준비 중": Vehicle Control, FusedLocation, TextToSpeech, SpeechToText. 제어 퍼미션 자체는 normal이라 에뮬레이터에서 동작하는지 스파이크로 확인한다.

## 6. 대체 구현 (SDK 미오픈 대응)

| 기능 | 지금 | Pleos SDK 오픈 후 |
|---|---|---|
| 음성 출력 | `android.speech.tts.TextToSpeech` | TextToSpeech SDK |
| 음성 인식 | `android.speech.SpeechRecognizer` | SpeechToText SDK |
| 위치 | `LocationManager` | FusedLocation SDK |
| 이야기 생성 | 외부 LLM (백엔드 프록시) | 변경 없음 |

`VoiceIO`, `LocationSource`를 인터페이스로 두고 구현체만 바꾼다.

## 7. 에뮬레이터 스파이크 결과 (2026-11-03, Pleos Connect Emulator v2.1.0)

| 항목 | 결과 |
|---|---|
| 퍼미션 7개(조회 4, 제어 3) | 설치 시 모두 granted |
| 조회: 브레이크, 크루즈, 설정온도, 창문 위치 | 성공 |
| 조회·구독: 트립 주행시간·거리 | `ERROR_NOT_SUPPORTED_PROPERTY` |
| 구독: 브레이크 | 성공. Virtual Console BRAKE 입력 → true/false 콜백 |
| 제어: 설정온도 18°C/23°C | 성공, Virtual Console에 반영 |
| 제어: 내기순환 off | 지원 영역으로 호출하면 성공 |
| 제어: 창문 2/10 | 성공 |
| 제어: 앰비언트 ON + 색 | CONTROL_CAR_LIGHTS 추가 + 지원 영역으로 호출하면 성공 |

- Vehicle Control SDK가 Playground에서 "준비 중"이어도 에뮬레이터에서는 3rd party 앱의 제어 호출이 동작한다. 실제 마켓 탑재 때 퍼미션 승인 여부는 별개다.
- Virtual Console(`adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`)로 기어, 브레이크, 크루즈, 공조, 창문, 앰비언트, 배터리를 주입할 수 있다. 트립 주행시간 카드는 없다.

## 8. 미결 사항

- [x] 에뮬레이터에서 HVAC·Light·Window set이 3rd party 앱으로 동작하는가 → 동작함
- [x] Virtual Console로 신호를 주입할 수 있는가 → 브레이크·크루즈·기어 가능, 트립 주행시간 불가
- [ ] AAOS에서 외부 네트워크(LLM 백엔드) 호출 제약
- [ ] NaviHelper로 ETA 조회·경유지 추가 API 확인

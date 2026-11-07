# 마중

현대자동차그룹 차량용 앱 개발 공모전(Pleos App Market) 출품작. 탑승할 사람이 폰에서 "데리러 와줘"를 누르면 차 화면에 알림이 뜨고, 운전자가 승인하면 서로의 위치와 도착 예정 시간이 공유된다. 탑승자가 가까이 오면 차가 실내등을 켜서 차를 찾게 하고, 기어·문 신호로 탑승을 알아채면 공유를 스스로 끝낸다.

2026-11-06 이전 컨셉 "동행"(AI 여정 동반자)에서 바꿨다. 같은 날 저장소 폴더를 `~/IdeaProjects/cotraveler`에서 `~/IdeaProjects/maejung`으로, 패키지를 `com.cotraveler`에서 `com.maejung`으로 바꿨다.

## 처음 온 에이전트가 할 일

1. 이 파일 → [docs/TASKS.md](docs/TASKS.md)(작업 순서와 완료 기준) → 맡은 작업의 설계 문서 순서로 읽는다.
2. TASKS.md에서 체크 안 된 가장 위 작업부터 한다. "담당: 사용자" 작업은 하지 않고 사용자에게 알린다.
3. 끝나면 완료 기준을 직접 실행해 확인하고, 결정·검증 결과를 설계 문서에 반영한다.
4. 커밋은 사용자가 요청할 때만 한다. 형식은 첫 줄 `[브랜치명] 제목`, 이어서 `- 변경 내용` 목록, 빈 줄 뒤 마지막 줄에 `Co-Authored-By: <모델 이름> <noreply@anthropic.com>`.
5. 문서끼리 어긋나면 기준은 이렇다: 통신은 PROTOCOL.md, 차 앱 상태 전이는 CAR_APP.md 3절, 폰 앱 상태 전이는 PHONE_APP.md 4절, 작업 범위와 완료 기준은 TASKS.md. 어긋난 곳을 발견하면 고치고 사용자에게 알린다.

## 설계 문서 (저장소, 코드와 같이 갱신)

| 문서 | 내용 |
|---|---|
| [docs/TASKS.md](docs/TASKS.md) | 작업 목록, 순서, 완료 기준, 담당 |
| [docs/DESIGN.md](docs/DESIGN.md) | 설계 원칙, 세션 상태, 탑승 판정, 실내등, Vehicle SDK 매핑(시그니처·퍼미션·import), 스파이크 결과 |
| [docs/PROTOCOL.md](docs/PROTOCOL.md) | 차·폰·서버 통신 계약(REST, WebSocket 메시지, 토큰, 세션 수명). 셋 중 하나를 바꾸면 이 문서부터 |
| [docs/SERVER.md](docs/SERVER.md) | 서버 스택·구조·테스트·배포 |
| [docs/CAR_APP.md](docs/CAR_APP.md) | 차 앱 모듈·패키지·상태 머신 표·화면 원칙 |
| [docs/PHONE_APP.md](docs/PHONE_APP.md) | 폰 앱 범위·화면·위치 전송 |

## 기획 문서 (Obsidian, 사업성·시나리오 원본)

- 기획서 초안: `~/Documents/Obsidian Vault/01. Projects/HMG 차량용 앱 공모전/HMG 차량용 앱 공모전 - 마중 기획서 초안.md`
- 개발 계획: `~/Documents/Obsidian Vault/01. Projects/HMG 차량용 앱 공모전/HMG 차량용 앱 공모전 - 마중 개발 계획.md`
- 요강 분석과 아이디어 선정 근거(심사 기준, 시상, 제출 규정, README·APK 규칙, 컨셉 변경 이유): `~/Documents/Obsidian Vault/01. Projects/HMG 차량용 앱 공모전/HMG 차량용 앱 공모전 - 요강 분석과 아이디어 선정.md`
- 이전 "동행" 문서(`… - 기획서 초안.md`, `… - 개발 계획.md`)는 보류(archived) 상태다.
- 새 계획이나 기능 문서도 같은 Obsidian 폴더에 저장한다.

## 일정

- 2026-11-15(일) 1차 마감: 기획서(필수) + 시연 영상, GitHub(소스+`apk/팀명_마중_release.apk`) → 가산점
- 2026-12-03~04 2차 오프라인 PT: 실제 동작하는 앱 필수, 여기서 순위 확정
- 제출: appdevchallenge@hyundai-partners.com, 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`

## 구성과 주요 결정

```
maejung/
├── app/        차 앱 (Pleos, AAOS)        ┐
├── phone/      폰 앱 (추가 예정)           ├ 루트 Android Gradle 빌드
├── protocol/   공용 메시지 모델 (추가 예정) ┘
├── server/     서버 (추가 예정, 독립 Gradle 프로젝트)
├── docs/       설계 문서
└── scripts/    에뮬레이터 보조 스크립트
```

- **서버는 직접 만든다**(2026-11-06). Eventoday 백엔드와 같은 Kotlin 2.0.21 / Spring Boot 3.4.5 / Java 21 / Docker + Railway. DB 없이 메모리에만 세션·위치를 둔다. Supabase는 검토 후 쓰지 않기로 했다.
- 차에는 GMS가 없어 FCM과 Google Maps를 못 쓴다. 요청은 WebSocket으로 받고, 1차 화면에는 지도 없이 거리·방향·ETA만 보여준다.
- 로그인 대신 차 화면의 6자리 연결 코드 + 서버 서명 토큰.
- **운전자 쪽 흐름은 알림과 음성으로만 진행한다**(2026-11-07). 아이의 요청이 지금 앞에 있는 앱(내비 등) 위에 헤드업 알림 + 음성으로 오고, 버튼이나 음성("응"/"아니")으로 답한다. 수락 시 차와 아이가 500m 이상이면 내비 길안내를 자동으로 시작한다. 마중 앱은 포그라운드 서비스로 뒤에서 계속 동작한다.
- 차량 제어는 실내등 하나만 쓴다. 탑승자가 50m 안에 오고 기어가 P면 실내등을 켜고, 끝나면 켜기 전 상태로 되돌린다(비상등은 SDK에 API가 없다). 기어·문·좌석은 읽기만 한다.
- 대기 모드(화면·공조 절약)와 약속 색 앰비언트는 사용자가 빼기로 했다(2026-11-06).
- 수익 모델의 "대기 중 주변 혜택"은 기획서에만 있고 1차 구현 범위가 아니다.
- PT 데모 축은 "수요일 밤 10시, 대치동 학원가" 시나리오 하나.

## Android 기술 스택

- 차 앱: Android Automotive(AAOS), Kotlin, minSdk 28. 폰 앱: minSdk 26. 둘 다 compileSdk 35(A1에서 34 → 35로 올림, 최신 AndroidX 요구), targetSdk 34.
- AGP 8.10.1, Kotlin 2.1.21, Gradle 8.14 wrapper
- 빌드 JDK: Android Studio 번들 JBR 21. 시스템 기본 JDK 25로는 빌드하지 않는다.
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Pleos Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2` (공개 Maven: nexus-playground.pleos.ai). 문서 예시의 2.3.1은 저장소에 없다.
- OkHttp 4.12.0 (HTTPS, WebSocket). UI는 Compose, JSON은 kotlinx.serialization(CAR_APP.md 1절).

## Pleos SDK 제약

- Playground 신청 화면에서는 Vehicle State·NaviHelper만 바로 사용 가능, Vehicle Control·FusedLocation·TextToSpeech·SpeechToText는 "준비 중"으로 나온다.
- 하지만 공개 Maven에 NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1, FusedLocation 1.1.3이 있고 에뮬레이터에서 동작한다(11/07 확인: NaviHelper 경로·ETA, TTS ON_DEVICE). 상세는 docs/DESIGN.md 5~7절.
- 에뮬레이터에 안드로이드 기본 TTS 엔진이 없다. 음성 출력은 Pleos TTS(`Mode.ON_DEVICE`)를 쓴다.
  → 음성은 Android `TextToSpeech`/`SpeechRecognizer`, 위치는 `LocationManager`로 구현하고 인터페이스로 감싸 SDK가 열리면 교체한다.
- 실내등·앰비언트 set에는 `CONTROL_CAR_LIGHTS`가 필요하다(문서에는 CAR_LIGHTS만 적혀 있는 경우가 있음). 앰비언트·내기순환은 차량 전체 영역이라 캐퍼빌리티가 준 영역으로 호출한다.
- `CAR_DOORS`, `CAR_SEATS`는 보호 수준 normal.
- `Safety.getTelltalesInfo`는 signature|privileged라 사용 불가.
- Vehicle API는 모두 비동기(onComplete/onFailed). `initialize()` 후 사용하고, 콜백이 끝나기 전에 `release()`하지 않는다.
- SDK 라이선스(42dot)가 디컴파일·역분석을 금지한다. 시그니처는 공식 API 레퍼런스(document.pleos.ai)에서 확인한다. 클래스 파일 이름 목록으로 import 경로를 찾는 것까지만 한다. 문서 본문은 Dokka shadowRoot 안에 있어서, 브라우저로 페이지를 연 뒤 `shadowRoot.textContent`를 읽어야 한다.

## 현재 상태

- `app/src/main/java/com/maejung/spike/VehicleSpikeActivity.kt`: SDK 스파이크. 조회·구독·제어, 외부 통신(HTTPS, WebSocket), 탑승 신호(기어, 문, 착석, 벨트), 실내등 확인 완료(docs/DESIGN.md 7절). 각성·바다 연출과 창문 제어 버튼은 이전 컨셉 검증 기록이다.
- 11/07: 서버(`server/`, 테스트 34개), 공용 메시지 모듈(`protocol/`, 테스트 9개), 차 앱 골격(A1, Compose 홈 화면), 상태 머신(A2, 테스트 41개), 차량 신호(A3)와 가짜 서버로 한 바퀴(A4, 에뮬레이터 확인) 완료. 다음은 A5(실제 서버 연결)·A8(알림·길안내). 폰 앱(P1~P4)은 별도 세션으로 병렬 진행 예정.
- 사용자는 2026-11-03 채팅으로 42dot SDK 라이선스에 동의했고, Pleos Connect Emulator 이미지가 설치되어 있다.

## 에뮬레이터 실행

- AVD 이름: `Pleos_Connect_v2_1` (API 34 `pleos_car_emulator` arm64, Cold boot, RAM 4GB). 보통 `emulator-5554`.
- 실행: `~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2_1 -no-snapshot-load` (백그라운드로)
- 설치: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (`-q` 옵션은 에뮬레이터가 거부한다)
- Virtual Console: `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`. 브라우저 폭이 좁으면 BODY 패널(문)이 잘리니 1600px 이상으로 본다. 실내등 상태는 콘솔에 표시되지 않는다.
- 화면 조작·로그 확인: `scripts/tap.sh "버튼 텍스트 정규식" [대기초]`, `scripts/log.sh`. `uiautomator dump`로 버튼 좌표와 로그 TextView를 읽는다. 버튼 텍스트에 괄호·점이 있으면 정규식으로 이스케이프한다. 화면이 바쁘면 dump가 실패("could not get idle state")하니, 로그는 `adb logcat -s MaejungSpike`로 본다. 다른 앱(내비 등)이 앞에 나와 있으면 탭이 그 앱으로 가니 `am start`로 먼저 앞으로 가져온다.
- 런타임 퍼미션은 `adb shell pm grant --user 10 com.maejung <퍼미션>`(차 에뮬레이터 앱은 사용자 10).
- 위치 주입: `adb emu geo fix <경도> <위도>`. 앱의 LocationManager에는 들어가지만, 내장 내비 앱은 이 값을 쓰지 않고 자체 기본 위치를 쓴다.
- 호스트 PC의 로컬 서버는 에뮬레이터에서 `10.0.2.2:8080`.

## 빌드

```bash
./gradlew :app:assembleDebug        # 차 앱
cd server && ./gradlew test bootRun  # 서버 (S1 이후)
```

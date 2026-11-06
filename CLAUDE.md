# 마중

현대자동차그룹 차량용 앱 개발 공모전(Pleos App Market) 출품작. 탑승할 사람이 폰에서 "데리러 와줘"를 누르면 차 화면에 알림이 뜨고, 운전자가 승인하면 서로의 위치와 도착 예정 시간이 공유된다. 차가 기어·문 신호로 탑승을 알아채면 공유를 스스로 끝낸다.

2026-11-06 이전 컨셉 "동행"(AI 여정 동반자)에서 바꿨다. 같은 날 저장소 폴더를 `~/IdeaProjects/cotraveler`에서 `~/IdeaProjects/maejung`으로, 패키지를 `com.cotraveler`에서 `com.maejung`으로 바꿨다.

## 기획 문서 (Obsidian, 원본은 여기)

- 기획서 초안: `~/Documents/Obsidian Vault/01. Projects/HMG 차량용 앱 공모전/HMG 차량용 앱 공모전 - 마중 기획서 초안.md`
- 개발 계획: `~/Documents/Obsidian Vault/01. Projects/HMG 차량용 앱 공모전/HMG 차량용 앱 공모전 - 마중 개발 계획.md`
- 요강 분석과 아이디어 선정 근거(심사 기준, 시상, 제출 규정, README·APK 규칙, 컨셉 변경 이유): `~/Documents/Obsidian Vault/01. Projects/HMG 차량용 앱 공모전/HMG 차량용 앱 공모전 - 요강 분석과 아이디어 선정.md`
- 이전 "동행" 문서(`… - 기획서 초안.md`, `… - 개발 계획.md`)는 보류(archived) 상태다.
- 새 계획이나 기능 문서도 같은 Obsidian 폴더에 저장한다.

## 설계 문서 (저장소)

- [docs/DESIGN.md](docs/DESIGN.md): 구성, 세션 상태, 탑승 판정, Vehicle SDK API 매핑(시그니처·퍼미션·import 경로), 대체 구현, 스파이크 결과. 코드를 바꾸면 같이 갱신한다.

## 일정

- 2026-11-15(일) 1차 마감: 기획서(필수) + 시연 영상, GitHub(소스+`apk/팀명_마중_release.apk`) → 가산점
- 2026-12-03~04 2차 오프라인 PT: 실제 동작하는 앱 필수, 여기서 순위 확정
- 제출: appdevchallenge@hyundai-partners.com, 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`

## 구성

- 차 앱(Pleos, 이 저장소 `app/`), 폰 앱(Android, 추가 예정), 서버(Supabase: Auth, Postgres, Realtime)
- 차에는 GMS가 없어 FCM을 못 쓴다. 차 앱은 WebSocket(Realtime)으로 요청을 받는다.
- 차량 제어는 실내등 하나만 쓴다. 탑승자가 50m 안에 오고 기어가 P면 실내등을 켜서 차를 찾게 하고, 끝나면 켜기 전 상태로 되돌린다(비상등은 SDK에 API가 없다). 기어·문·좌석은 읽기만 한다.
- 대기 모드(화면·공조 절약)와 약속 색 앰비언트는 사용자가 빼기로 했다(2026-11-06).
- PT 데모 축은 "수요일 밤 10시, 대치동 학원가" 시나리오 하나.

## 기술 스택

- Android Automotive(AAOS) 네이티브, Kotlin, minSdk 28, compileSdk 34
- AGP 8.10.1, Kotlin 2.1.21, Gradle 8.14 wrapper
- 빌드 JDK: Android Studio 번들 JBR 21. 시스템 기본 JDK 25로는 빌드하지 않는다.
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Pleos Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2` (공개 Maven: nexus-playground.pleos.ai). 문서 예시의 2.3.1은 저장소에 없다.
- OkHttp 4.12.0 (HTTPS, WebSocket)

## Pleos SDK 제약

- 바로 사용 가능: Vehicle State(조회), NaviHelper
- 준비 중(신청 불가): Vehicle Control, FusedLocation, TextToSpeech, SpeechToText
  → 음성은 Android `TextToSpeech`/`SpeechRecognizer`, 위치는 `LocationManager`로 구현하고 인터페이스로 감싸 SDK가 열리면 교체한다.
- 앰비언트 라이트 set에는 `CONTROL_CAR_LIGHTS`가 필요하다(문서에는 CAR_LIGHTS만 적혀 있음). 앰비언트·내기순환은 차량 전체 영역이라 캐퍼빌리티가 준 영역으로 호출한다.
- `CAR_DOORS`, `CAR_SEATS`는 보호 수준 normal.
- `Safety.getTelltalesInfo`는 signature|privileged라 사용 불가.
- Vehicle API는 모두 비동기(onComplete/onFailed). `initialize()` 후 사용하고, 콜백이 끝나기 전에 `release()`하지 않는다.
- SDK 라이선스(42dot)가 디컴파일·역분석을 금지한다. 시그니처는 공식 API 레퍼런스(document.pleos.ai)에서 확인한다. 클래스 파일 이름 목록으로 import 경로를 찾는 것까지만 한다.

## 현재 상태

- `app/src/main/java/com/maejung/spike/VehicleSpikeActivity.kt`: SDK 스파이크. 조회·구독·제어, 외부 통신(HTTPS, WebSocket), 탑승 신호(기어, 문, 착석, 벨트) 확인 완료(docs/DESIGN.md 7절). 각성·바다 연출과 창문 제어 버튼은 이전 컨셉 검증 기록으로만 남아 있다.
- 사용자는 2026-11-03 채팅으로 42dot SDK 라이선스에 동의했고, Pleos Connect Emulator 이미지가 설치되어 있다.

## 에뮬레이터 실행

- AVD 이름: `Pleos_Connect_v2_1` (API 34 `pleos_car_emulator` arm64, Cold boot, RAM 4GB)
- 실행: `~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2_1 -no-snapshot-load` (백그라운드로)
- 설치: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (`-q` 옵션은 에뮬레이터가 거부한다)
- Virtual Console: `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`. 브라우저 폭이 좁으면 BODY 패널(문)이 잘리니 1600px 이상으로 본다.
- 화면 조작·로그 확인은 `uiautomator dump`로 버튼 좌표와 텍스트를 읽어 `adb shell input tap`으로 누른다. 버튼 텍스트에 괄호가 있으면 정규식으로 이스케이프한다.

## 빌드

```bash
./gradlew :app:assembleDebug
```

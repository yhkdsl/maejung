# 동행 (Co-traveler)

현대자동차그룹 차량용 앱 개발 공모전(Pleos App Market) 출품작. 차량 상황을 읽어 이야기와 실내 연출로 여정을 채우고, 운전자가 지치면 깨워서 휴게소로 안내하는 AI 여정 동반자 앱.

## 기획 문서 (Obsidian, 원본은 여기)

- 기획서 초안: `~/Documents/Obsidian Vault/01. Projects/HMG 차량용 앱 공모전/HMG 차량용 앱 공모전 - 기획서 초안.md`
- 개발 계획: `~/Documents/Obsidian Vault/01. Projects/HMG 차량용 앱 공모전/HMG 차량용 앱 공모전 - 개발 계획.md`
- 요강 분석과 아이디어 선정 근거(심사 기준, 시상, 제출 규정, README·APK 규칙): `~/Documents/Obsidian Vault/01. Projects/HMG 차량용 앱 공모전/HMG 차량용 앱 공모전 - 요강 분석과 아이디어 선정.md`
- 새 계획이나 기능 문서도 같은 Obsidian 폴더에 저장한다.

## 설계 문서 (저장소)

- [docs/DESIGN.md](docs/DESIGN.md): 모드·우선순위, 컴포넌트, 피로 점수, 연출 태그, Vehicle SDK API 매핑(시그니처·퍼미션·import 경로), 대체 구현. 코드를 바꾸면 같이 갱신한다.

## 일정

- 2026-11-15(일) 1차 마감: 기획서(필수) + 시연 영상, GitHub(소스+`apk/팀명_동행_release.apk`) → 가산점
- 2026-12-03~04 2차 오프라인 PT: 실제 동작하는 앱 필수, 여기서 순위 확정
- 제출: appdevchallenge@hyundai-partners.com, 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`

## 컨셉

모드 우선순위는 각성 > 키즈 스토리 > 도슨트 (확장: 휴식 모드).
핵심 기능(동화, 도슨트, 졸음 대화·휴게소 안내)은 음성으로 완결하고, 차량 제어는 Light·HVAC 연출 레이어로만 쓴다. 창문·도어 제어는 하지 않는다(2026-11-03 결정).
PT 데모 축은 "명절 귀성길 서울→부산 4시간" 시나리오 하나로 세 모드를 이어서 보여주는 것.

## 기술 스택

- Android Automotive(AAOS) 네이티브, Kotlin, minSdk 28, compileSdk 34
- AGP 8.10.1, Kotlin 2.1.21, Gradle 8.14 wrapper
- 빌드 JDK: Android Studio 번들 JBR 21. 시스템 기본 JDK 25로는 빌드하지 않는다.
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Pleos Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2` (공개 Maven: nexus-playground.pleos.ai). 문서 예시의 2.3.1은 저장소에 없다.

## Pleos SDK 제약 (2026-10-24 확인)

- 바로 사용 가능: Vehicle State(조회), NaviHelper
- 준비 중(신청 불가): Vehicle Control, FusedLocation, TextToSpeech, SpeechToText
  → 음성은 Android `TextToSpeech`/`SpeechRecognizer`, 위치는 `LocationManager`로 구현하고 인터페이스로 감싸 SDK가 열리면 교체한다.
- 제어 퍼미션(`CONTROL_CAR_CLIMATE`, `CONTROL_CAR_WINDOWS`)은 보호 수준 normal. 앰비언트 라이트 set은 `CAR_LIGHTS`만 요구한다.
- `Safety.getTelltalesInfo`는 signature|privileged라 사용 불가 → 피로 점수에서 제외.
- Vehicle API는 모두 비동기(onComplete/onFailed). `initialize()` 후 사용하고, 콜백이 끝나기 전에 `release()`하지 않는다.
- SDK 라이선스(42dot)가 디컴파일·역분석을 금지한다. 시그니처는 공식 API 레퍼런스(document.pleos.ai)에서 확인한다.
- LLM은 공모전 SDK에 없다 → 외부 LLM API를 백엔드 프록시로 호출한다(앱에 API 키를 넣지 않는다).

## 현재 상태

- `app/src/main/java/com/cotraveler/spike/VehicleSpikeActivity.kt`: 조회·구독·제어(각성/바다 연출) 스파이크. 에뮬레이터에서 동작 확인 완료(결과는 docs/DESIGN.md 7절).
- 앰비언트 라이트·내기순환은 차량 전체 영역이라 캐퍼빌리티가 준 영역으로 호출해야 하고, 앰비언트 set에는 `CONTROL_CAR_LIGHTS`가 필요하다. 트립 주행시간은 에뮬레이터 미지원.
- 사용자는 2026-11-03 채팅으로 42dot SDK 라이선스에 동의했고, Pleos Connect Emulator 이미지가 설치되어 있다.

## 에뮬레이터 실행

- AVD 이름: `Pleos_Connect_v2_1` (API 34 `pleos_car_emulator` arm64, Cold boot, RAM 4GB)
- 실행: `~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2_1 -no-snapshot-load` (백그라운드로)
- 설치: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (`-q` 옵션은 에뮬레이터가 거부한다)
- Virtual Console: `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`
- 화면 조작·로그 확인은 `uiautomator dump`로 버튼 좌표와 텍스트를 읽어 `adb shell input tap`으로 누른다.

## 빌드

```bash
./gradlew :app:assembleDebug
```

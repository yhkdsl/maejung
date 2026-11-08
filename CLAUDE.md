# 마중

현대자동차그룹 차량용 앱 개발 공모전(Pleos App Market) 출품작. 탑승할 사람이 폰에서 "데리러 와줘"를 누르면 차 화면에 알림이 뜨고, 운전자가 승인하면 서로의 위치와 도착 예정 시간이 공유된다. 탑승자가 가까이 오면 차가 실내등을 켜서 차를 찾게 하고, 기어·문 신호로 탑승을 알아채면 공유를 스스로 끝낸다.

2026-11-06 이전 컨셉 "동행"(AI 여정 동반자)에서 바꿨다. 같은 날 저장소 폴더를 `~/IdeaProjects/cotraveler`에서 `~/IdeaProjects/maejung`으로, 패키지를 `com.cotraveler`에서 `com.maejung`으로 바꿨다.

## 처음 온 에이전트가 할 일

1. 이 파일 → [docs/TASKS.md](docs/TASKS.md)(작업 순서와 완료 기준) → 맡은 작업의 설계 문서 순서로 읽는다.
2. 사용자가 요청한 범위에서 작업한다. 개발을 이어 하라는 요청이면 TASKS.md의 미완료 작업과 의존 관계를 확인한다. "담당: 사용자" 작업은 하지 않고 사용자에게 알린다.
3. 끝나면 완료 기준을 직접 실행해 확인하고, 결정·검증 결과를 설계 문서에 반영한다.
4. 커밋은 사용자가 요청할 때만 한다. 형식은 첫 줄 `[브랜치명] 제목`, 이어서 `- 변경 내용` 목록, 빈 줄 뒤 마지막 줄에 `Co-Authored-By: <모델 이름> <noreply@anthropic.com>`.
5. 구현 현황은 실제 코드를 기준으로 쓴다. 문서가 코드와 다르면 현황 설명을 고치고, 미구현 설계와 검증 대기는 구분한다. 통신 문서는 PROTOCOL.md, 차 앱 상태 전이는 CAR_APP.md 3절, 폰 앱 P2 화면·미리보기는 PHONE_APP.md 2~3절, P3 예정 통신 상태 전이는 같은 문서 4절, 작업 범위와 완료 기준은 TASKS.md에서 관리한다. 기능을 바꿀 때는 관련 문서도 함께 갱신한다.

## 설계 문서 (저장소, 코드와 같이 갱신)

| 문서 | 내용 |
|---|---|
| [docs/TASKS.md](docs/TASKS.md) | 작업 목록, 순서, 완료 기준, 담당 |
| [docs/DESIGN.md](docs/DESIGN.md) | 설계 원칙, 세션 상태, 탑승 판정, 실내등, Vehicle SDK 매핑(시그니처·퍼미션·import), 스파이크 결과 |
| [docs/PROTOCOL.md](docs/PROTOCOL.md) | 차·폰·서버 통신 계약(REST, WebSocket 메시지, 토큰, 세션 수명). 셋 중 하나를 바꾸면 이 문서부터 |
| [docs/SERVER.md](docs/SERVER.md) | 서버 스택·구조·테스트·배포 |
| [docs/CAR_APP.md](docs/CAR_APP.md) | 차 앱 모듈·패키지·상태 머신 표·화면 원칙 |
| [docs/PHONE_APP.md](docs/PHONE_APP.md) | 폰 앱 P2 모듈·오프라인 UI와 P3 통신·P4 위치 전송 설계 |

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
├── app/        차 앱 (Pleos, AAOS) ┐
├── phone/      폰 앱 (P2 UI)       ├ 루트 Android Gradle 빌드
├── protocol/   공용 메시지 모델     ┘
├── server/     중계 서버 (독립 Gradle 프로젝트)
├── docs/       설계 문서
└── scripts/    가짜 탑승자 클라이언트·에뮬레이터 보조 스크립트
```

`phone/`은 P2에서 추가됐으며 오프라인 UI 구현·debug 빌드·폰 AVD 검증을 완료했다. 실제 REST·WebSocket·토큰 보관은 P3, GPS·위치 서비스는 P4에서 구현한다.

- **서버 직접 구현**(2026-11-06 결정, 11/07 구현). Eventoday 백엔드와 같은 Kotlin 2.0.21 / Spring Boot 3.4.5 / Java 21 / Docker + Railway 구성. DB 없이 세션·연결·페어링 정보를 메모리에 둔다. 좌표는 보관하지 않고 중계한다. Supabase는 검토 후 쓰지 않기로 했다.
- 차에는 GMS가 없어 FCM과 Google Maps를 못 쓴다. 요청은 WebSocket으로 받고, 공유 화면에는 osmdroid 6.1.20 기반 OpenStreetMap 지도(차·탑승자·50m 원)와 거리·8방위·ETA를 표시한다.
- 로그인 대신 차 화면의 6자리 연결 코드 + 서버 서명 토큰.
- **운전자 요청 대응은 알림과 음성이 기본**(2026-11-07). 지금 앞에 있는 앱(내비 등) 위에 헤드업 알림 + 음성으로 오고, 버튼이나 음성("응"/"아니")으로 답한다. 마중 앱 자체에도 수락·거절 화면이 있다. 음성 응답 코드는 구현됐지만 실제 발화 검증은 대기 중이다. 수락 후 첫 탑승자 좌표와 차 위치로 계산한 거리가 500m 이상이면 내비 길안내를 한 번 시작한다. 앱을 연 뒤 위치 권한이 있으면 포그라운드 서비스로 백그라운드 수신을 유지한다(부팅 시 자동 시작은 미구현).
- 현재 픽업 흐름의 차량 제어는 실내등 하나만 쓴다. 탑승자가 50m 이내이고 기어가 P면 켜고, 70m 초과·P 이탈·세션 종료 시 이전 상태로 되돌린다. 탑승 판정은 P + 30m 이내 + Nearby 진입 후 승객 문 열림→닫힘이다. 기어·문은 읽기만 하며 좌석은 과거 스파이크에서만 확인했다.
- 대기 모드(화면·공조 절약)와 약속 색 앰비언트는 사용자가 빼기로 했다(2026-11-06).
- 수익 모델의 "대기 중 주변 혜택"은 1차 영상용 데모 화면만 있다(11/08). 차 공유 화면에서 기어 P일 때 지어낸 예시 쿠폰과 "제휴 예시"를 보여준다. 실제 광고·제휴·정산은 없으며 구현된 사업 실적처럼 쓰지 않는다(CAR_APP.md 4절).
- PT 데모 축은 "수요일 밤 10시, 대치동 학원가" 시나리오 하나.

## Android 기술 스택

- 차 앱: Android Automotive(AAOS), Kotlin, minSdk 28, compileSdk 35(A1에서 34 → 35로 올림, 최신 AndroidX 요구), targetSdk 34. 폰 앱은 일반 Android 앱으로 minSdk 26, compileSdk 35, targetSdk 34, JVM 17이며 `com.maejung.phone`을 사용한다. Compose BOM `2025.05.01`·Material3와 `:protocol`을 재사용하고 P2에는 인터넷·GPS 권한, GMS·Pleos 의존성이 없다.
- AGP 8.10.1, Kotlin 2.1.21, Gradle 8.14 wrapper
- 빌드 JDK: Android Studio 번들 JBR 21. 시스템 기본 JDK 25로는 빌드하지 않는다.
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Pleos Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2` (공개 Maven: nexus-playground.pleos.ai). 문서 예시의 2.3.1은 저장소에 없다.
- OkHttp 4.12.0 (HTTPS, WebSocket). UI는 Compose, JSON은 kotlinx.serialization(CAR_APP.md 1절).

## Pleos SDK 제약

- Playground 신청 화면에서는 Vehicle State·NaviHelper만 바로 사용 가능, Vehicle Control·FusedLocation·TextToSpeech·SpeechToText는 "준비 중"으로 나온다.
- 공개 Maven 의존성과 구현은 NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1이다. 11/07 기록에서 길안내·ETA, TTS ON_DEVICE, STT 인식 준비 콜백을 확인했다. 실제 발화 인식은 미확인이다. FusedLocation 1.1.3은 조사 기록만 있으며 현재 차 앱은 의존하지 않는다. 상세는 docs/DESIGN.md 5~7절.
- 에뮬레이터에 안드로이드 기본 TTS 엔진이 없다. 말하기·듣기는 `PleosVoiceIO`에서 Pleos TTS·STT의 `Mode.ON_DEVICE`를 쓴다. Android `TextToSpeech`/`SpeechRecognizer`는 현재 구현이 아니다.
- 위치는 `AndroidLocationSource`의 Android `LocationManager`를 사용한다. SDK 교체 작업은 현재 구현 범위에 포함하지 않는다.
- 실내등·앰비언트 set에는 `CONTROL_CAR_LIGHTS`가 필요하다(문서에는 CAR_LIGHTS만 적혀 있는 경우가 있음). 앰비언트·내기순환은 차량 전체 영역이라 캐퍼빌리티가 준 영역으로 호출한다.
- `CAR_DOORS`, `CAR_SEATS`는 보호 수준 normal.
- `Safety.getTelltalesInfo`는 signature|privileged라 사용 불가.
- Vehicle API는 모두 비동기(onComplete/onFailed). `initialize()` 후 사용하고, 콜백이 끝나기 전에 `release()`하지 않는다.
- SDK 라이선스(42dot)가 디컴파일·역분석을 금지한다. 시그니처는 공식 API 레퍼런스(document.pleos.ai)에서 확인한다. 클래스 파일 이름 목록으로 import 경로를 찾는 것까지만 한다. 문서 본문은 Dokka shadowRoot 안에 있어서, 브라우저로 페이지를 연 뒤 `shadowRoot.textContent`를 읽어야 한다.

## 현재 상태

- `app/src/main/java/com/maejung/spike/VehicleSpikeActivity.kt`: SDK 스파이크. 조회·구독·제어, 외부 통신(HTTPS, WebSocket), 탑승 신호(기어, 문, 착석, 벨트), 실내등 확인 완료(docs/DESIGN.md 7절). 각성·바다 연출과 창문 제어 버튼은 이전 컨셉 검증 기록이다.
- **기준: 2026-11-07, 코드 `74821e3`**. 서버 S1~S3, 공용 모듈 M1, 차 앱 A1~A9 구현이 있다. 로컬 서버·가짜 탑승자와의 흐름, 재연결, 지도 화면은 11/07 에뮬레이터 검증 기록이 있다. 실제 Android 폰 앱과의 통합은 아직 하지 않았다.
- **P1/P2 완료(11/07)**: `:phone` 빌드 설정·Manifest와 연결·홈·마중 중·요청 대기·종료 6가지 디버그 오프라인 UI를 구현했다. 실제 UI 구조는 `PhoneApp`·세 화면·`PhoneComponents`·`PhoneViewModel`·`PhoneState`다. `BuildConfig.DEBUG`로 미리보기를 제한하며 release는 P3 전까지 연결·요청 불가 안내를 표시한다. release 차단은 소스·단위 테스트 근거이며 release APK·런타임은 미검증이다.
- 11/07 점검에서 테스트를 강제 재실행해 차 앱 46개(상태 머신 38 + 조사·음성 답 3 + ETA 5), 공용 모듈 9개, 서버 34개, **총 89개 통과**를 확인했다. debug 빌드 명령도 성공했다.
- P2 새 검증: phone 테스트 16개·debug 조립 성공(7초), 메인에서 JBR 21로 `:protocol:test :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --offline --no-daemon --console=plain` 성공(12초·47개 태스크 실행). app 46 + protocol 9 = 55개, 실패·오류·건너뜀 0. 서버 소스 변경 없이 재실행하지 않았으므로 server 34는 이전 결과다. 폰 lint는 온라인 실행 성공(28초), 오류 0·경고 8개이며 release 주소 누락 dry-run은 예상 실패로 차단을 확인했다.
- 메인 폰 QA: APK 설치·런처 실행, 세 화면·요청 대기·정보 없음·근접·종료 6가지와 긴 문구 줄바꿈, 입력 오류·키보드 스크롤·종료 확인, 회전·백그라운드 프로세스 재생성 후 20m 상태 복원을 확인했다. 상세와 검증 경계는 PHONE_APP.md 6절. 실제 차 알림·통신·GPS는 검증한 것이 아니다.
- 남은 작업: P3/P4 폰 서버 연동·위치 서비스, A6 실제 발화 확인, S4 Docker 이미지 빌드 검증·S5 Railway 배포, D1~D5 통합·제출. README에는 폰 오프라인 미리보기 방법을 추가했고 D2의 실제 서버 연동·제출 APK 정보는 P3/P4·D3 후 보완한다.
- 사용자는 2026-11-03 채팅으로 42dot SDK 라이선스에 동의했고, Pleos Connect Emulator 이미지가 설치되어 있다.

## 에뮬레이터 실행

- AVD 이름: `Pleos_Connect_v2_1` (API 34 `pleos_car_emulator` arm64, Cold boot, RAM 4GB). 보통 `emulator-5554`.
- 폰 AVD: `Medium_Phone_API_36.0` (API 36, Google APIs Play Store, arm64, RAM 2GB). 기존 이미지가 설치돼 있어 다운로드 없이 11/07 차 AVD와 동시 부팅을 확인했다. 실행: `~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.0 -port 5556 -no-snapshot-load`. serial은 `adb devices`로 확인하며, 이번 검증에서는 차 `emulator-5554`, 폰 `emulator-5556`을 사용했다.
- 폰 P2 미리보기 설치·실행: `adb -s emulator-5556 install -r phone/build/outputs/apk/debug/phone-debug.apk` → `adb -s emulator-5556 shell am start -n com.maejung.phone/.MainActivity`(11/07 조립·설치·화면 확인). 여러 기기를 켜면 모든 adb 명령의 `-s`를 실제 serial에 맞춘다. 미리보기는 실제 차에 알림을 보내지 않는다.
- 실행: `~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2_1 -no-snapshot-load` (백그라운드로)
- 설치: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (`-q` 옵션은 에뮬레이터가 거부한다)
- Virtual Console: `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`. 브라우저 폭이 좁으면 BODY 패널(문)이 잘리니 1600px 이상으로 본다. 실내등 상태는 콘솔에 표시되지 않는다.
- 화면 조작·로그 확인: `scripts/tap.sh "버튼 텍스트 정규식" [대기초]`, `scripts/log.sh`. `uiautomator dump`로 버튼 좌표와 로그 TextView를 읽는다. 버튼 텍스트에 괄호·점이 있으면 정규식으로 이스케이프한다. 화면이 바쁘면 dump가 실패("could not get idle state")하니, 로그는 `adb logcat -s MaejungSpike`로 본다. 다른 앱(내비 등)이 앞에 나와 있으면 탭이 그 앱으로 가니 `am start`로 먼저 앞으로 가져온다.
- 런타임 퍼미션은 `adb shell pm grant --user 10 com.maejung <퍼미션>`(차 에뮬레이터 앱은 사용자 10).
- 위치 주입: `adb emu geo fix <경도> <위도>`. 앱의 LocationManager에는 들어가지만, 내장 내비 앱은 이 값을 쓰지 않고 자체 기본 위치를 쓴다.
- 호스트 PC의 로컬 서버는 에뮬레이터에서 `10.0.2.2:8080`.
- 차 앱 로그: `adb logcat -s CarSession WebSocketTransport`(상태 전이와 효과가 한 줄씩 찍힌다).
- **폰 없이 끝까지 돌려 보기**: 로컬 서버를 띄우고(`cd server && ./gradlew bootRun`), 차 앱 홈에서 "가족 연결"로 6자리 코드를 받은 뒤 `node scripts/fake-rider.mjs <코드>`를 실행한다. 페어링 → 요청 → (차에서 수락) → 학원에서 차까지 2초 간격으로 걸어온다. 차 위치는 `adb emu geo fix 127.06030 37.49950`, 탑승은 Virtual Console BODY 패널 RR 문 열고 닫기.
- 서버 없이 차 앱만 볼 때: `./gradlew :app:assembleDebug -Pmaejung.fakeServer=true`로 빌드하면 홈에 "가짜 요청 받기" 버튼이 생긴다.

## 빌드

```bash
./gradlew :app:assembleDebug        # 차 앱
./gradlew :phone:assembleDebug      # 폰 P2 오프라인 UI
./gradlew :phone:testDebugUnitTest  # 폰 입력·미리보기 상태 테스트 16개
./gradlew :protocol:test :app:testDebugUnitTest  # 공용 모듈·차 앱 테스트
cd server && ./gradlew test bootRun  # 서버
```

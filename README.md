# 마중

현대자동차그룹 차량용 앱 개발 공모전(Pleos App Market) 출품작. 픽업 요청을 승인하면 서로의 위치와 ETA를 공유하고, 가까이 오면 실내등으로 차를 찾게 하며, 승객 문 신호로 탑승을 감지해 공유를 끝내는 차량 앱입니다.

## 구현 상태

2026-11-07, 기존 코드 `74821e3`와 작업 중인 P2 변경 기준입니다.

- **차 앱 구현**: 가족 연결 코드, 헤드업 요청 알림·수락·거절, Pleos TTS/STT, 위치·ETA 공유, OpenStreetMap 지도, 500m 이상일 때 길안내 시작, 근접 시 실내등 제어, 탑승 감지·공유 종료.
- **서버 구현**: 차 등록, 코드 페어링, 서명 토큰, WebSocket 중계, 세션 만료. DB 없이 세션·연결 정보를 메모리에 두며 좌표는 수신 즉시 상대에게 중계합니다.
- **검증 대기**: 실제 발화로 음성 승인·거절, Docker 이미지 빌드, Railway 배포.
- **폰 앱 P2 완료**: `phone/` 모듈과 연결·홈·마중 중 화면, 요청 대기·종료 6가지를 구현하고 debug 빌드·폰 AVD에서 확인했습니다. 디버그 전용 오프라인 미리보기이며 실제 서버·차와 연결하지 않습니다. 실제 REST·WebSocket·토큰 보관(P3), GPS·위치 서비스(P4)는 미구현입니다. 서버와 차를 함께 시연할 때는 `scripts/fake-rider.mjs`가 탑승자 역할을 대신합니다. 상세는 [PHONE_APP.md](docs/PHONE_APP.md)에 있습니다.
- **제출 준비 중**: 서명된 release APK, 통합 데모 스크립트·영상, 제출용 기획서, GitHub 게시.

## 구성

| 폴더 | 내용 |
|---|---|
| `app/` | 차 앱 (Pleos Connect, Android Automotive) |
| `phone/` | 탑승자 폰 앱 (P2 오프라인 UI 완료, P3/P4 미구현) |
| `protocol/` | 공용 REST·WebSocket 메시지 모델 (Kotlin/JVM) |
| `server/` | 중계 서버 (독립 Gradle 프로젝트, Kotlin, Spring Boot, WebSocket) |
| `docs/` | 설계 문서 |
| `scripts/` | 가짜 탑승자 클라이언트, 화면 조작·로그 확인 도구 |

## 개발·테스트 환경

- JDK 21. macOS에서는 Android Studio 번들 JBR을 사용합니다.
- 차 앱: compileSdk 35, targetSdk 34, minSdk 28. Android SDK 경로는 Android Studio 또는 루트 `local.properties`의 `sdk.dir`로 설정합니다.
- 폰 앱: compileSdk 35, targetSdk 34, minSdk 26, JVM 17. 기존 Compose BOM `2025.05.01`을 사용합니다. P2 Manifest에는 인터넷·위치 권한이 없습니다.
- 검증에 사용한 차 AVD: Pleos Connect Emulator v2.1, `Pleos_Connect_v2_1` (API 34, arm64). 일반 폰 에뮬레이터에서는 Pleos 차량 SDK 기능을 검증할 수 없습니다.
- 폰 AVD: 기존 `Medium_Phone_API_36.0` (API 36, Google APIs Play Store, arm64). 11/07 추가 이미지 다운로드 없이 차 AVD와 동시 부팅했고 `adb devices`의 두 device와 폰 `sys.boot_completed=1`을 확인했습니다(P1 완료). 폰 `emulator-5556`에서 P2 debug APK 설치·실행·화면을 확인했습니다.
- 차 앱 debug 서버 주소는 `http://10.0.2.2:8080`입니다. 호스트에서는 `http://localhost:8080`을 사용합니다.
- 가짜 탑승자 스크립트는 내장 WebSocket을 지원하는 Node.js 22 이상이 필요합니다.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :protocol:test :app:testDebugUnitTest :app:assembleDebug
```

11/07 P2 검증에서 JBR 21로 **폰 16개 테스트 통과·debug 조립 성공**, `--rerun-tasks --offline --no-daemon --console=plain`으로 위 차·공용 명령을 실행해 **차 앱 46 + 공용 모듈 9 = 55개 통과**(실패·오류·건너뜀 0), 차 debug 조립 성공을 확인했습니다. 서버 소스는 변경하지 않아 P2에서 서버 테스트는 재실행하지 않았고 이전 34개 결과를 유지합니다. 이전 전체 89개 결과에 폰 16개가 포함된 것은 아닙니다. 폰 lint는 **오류 0·경고 8개**로 성공했으며 경고는 targetSdk 34, 고정 의존성 5건, dataExtractionRules, 아이콘 누락입니다. 실제 발화 STT는 아직 확인하지 않았습니다.

## 서버

### 로컬 실행

JDK 21이 필요합니다.

```bash
cd server
./gradlew test       # 단위·통합 테스트
./gradlew bootRun    # http://localhost:8080
```

서버가 실행되면 다른 터미널에서 `curl localhost:8080/health`로 확인합니다(`{"status":"ok"}`).

### 차 앱과 가짜 탑승자 실행

서버를 실행한 상태에서 저장소 루트의 다른 터미널에서 진행합니다.

```bash
~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2_1 -no-snapshot-load &
```

에뮬레이터 부팅이 끝난 뒤 설치·실행합니다.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.maejung/.MainActivity
```

첫 실행 때 위치·마이크·알림 권한을 허용합니다. 여러 에뮬레이터를 실행 중이면 각 `adb` 명령에 `-s <차 에뮬레이터 ID>`를 지정합니다.

1. 차 위치를 `adb emu geo fix 127.06030 37.49950`으로 주입합니다(경도, 위도 순).
2. 차 앱의 **가족 연결**을 눌러 6자리 코드를 받습니다.
3. `node scripts/fake-rider.mjs <연결코드>`를 실행합니다. 선택 인자는 `[서버 주소] [이름]`입니다.
4. 차에서 수락하면 스크립트가 학원 좌표에서 차까지 2초 간격으로 위치를 보냅니다.
5. `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`의 Virtual Console에서 기어 P와 승객 문(RR 등) 열림→닫힘을 주입해 근접·탑승 흐름을 확인합니다.

서버 없이 차 앱 흐름만 볼 때는 `./gradlew :app:assembleDebug -Pmaejung.fakeServer=true`로 다시 빌드·설치하고 홈의 **가짜 요청 받기**를 누릅니다. 이후 실제 서버에 연결할 때는 해당 옵션 없이 다시 빌드·설치합니다. 두 모드 모두 차량 신호·음성·위치는 실제 어댑터를 사용합니다.

로그는 `adb -s <차 에뮬레이터 ID> logcat -s CarSession WebSocketTransport`로 확인합니다.

### 폰 앱 오프라인 미리보기 (P2)

다음은 P2 debug APK의 빌드·설치·실행 방법입니다. 11/07 메인 QA에서 폰 AVD 설치·런처 실행과 아래 미리보기 흐름을 확인했습니다. 서버 실행은 필요 없습니다.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :phone:testDebugUnitTest :phone:assembleDebug
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.0 -port 5556 -no-snapshot-load &
adb devices -l
adb -s emulator-5556 install -r phone/build/outputs/apk/debug/phone-debug.apk
adb -s emulator-5556 shell am start -n com.maejung.phone/.MainActivity
```

이미 켜져 있는 AVD는 다시 실행할 필요가 없습니다. serial은 `adb devices` 결과에 맞춰 바꿉니다(11/07 동시 부팅: 차 `emulator-5554`, 폰 `emulator-5556`). 설치·실행·로그·위치 주입 등 각 `adb` 명령에 `-s`로 대상 기기를 지정합니다.

이름과 코드를 넣고 **입력 확인 후 홈 미리보기** → **데리러 와줘 · 미리보기**를 누르면 요청 대기 예시가 됩니다. 이름은 앞뒤 공백 제거 후 Kotlin `String.length` 1~20, 코드는 ASCII 숫자(`0`~`9`) 정확히 6자리입니다. 자동 승인·공유 전환은 없으며 하단 **오프라인 미리보기 메뉴**에서 **공유 · 예시 120m**, **공유 · 정보 없음**, **근접 · 예시 20m**이나 종료 6가지를 직접 고릅니다.

미리보기의 연결·요청·실내등 표시는 폰 안의 샘플 상태이며 **실제 차 알림·위치 공유·실내등 제어를 일으키지 않습니다.** `BuildConfig.DEBUG`가 미리보기를 허용하며 release는 P3 서버 연동 전까지 연결·요청을 사용할 수 없다는 안내와 비활성 버튼을 제공합니다. release 미리보기 차단은 소스·단위 테스트로 확인했고 release APK·런타임은 검증하지 않았습니다.

P2 화면 QA에서는 세 화면·요청 대기·정보 없음·근접·종료 6가지의 표시, 입력 오류·키보드 스크롤·종료 확인·회전 후 입력 유지·백그라운드 프로세스 재생성 후 20m 근접 상태 복원을 확인했습니다. 상세 근거와 범위는 [PHONE_APP.md 6절](docs/PHONE_APP.md#6-p1p2-검증-결과)에 있습니다.

### Railway 배포

1. Railway 프로젝트에 서비스를 추가하고 이 저장소를 연결합니다.
2. **Root Directory**를 `server`로 지정합니다. `server/Dockerfile`로 빌드됩니다.
3. 환경변수를 넣습니다.
   - `SPRING_PROFILES_ACTIVE=railway`
   - `MAEJUNG_TOKEN_SECRET`: 32자 이상 임의 문자열 (예: `openssl rand -base64 48`). 바꾸면 모든 차·폰 연결이 무효가 됩니다.
4. 헬스체크 경로를 `/health`로 지정합니다.
5. 배포 URL을 차·폰 앱 release 빌드의 `maejung.serverUrl`에 넣습니다(`local.properties` 또는 `-Pmaejung.serverUrl=https://...`). 주소가 없으면 release 빌드가 실패합니다. 폰도 `BuildConfig.SERVER_URL` 설정은 있으나 실제 통신은 P3에서 구현합니다.

현재 배포 설정은 작성됐지만 Docker 이미지 빌드 성공과 Railway 배포는 아직 검증하지 않았습니다. release 서명 설정도 남아 있습니다.

## APK와 남은 작업

- 차 앱 debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- 폰 앱 debug APK: `phone/build/outputs/apk/debug/phone-debug.apk` (11/07 조립·폰 AVD 설치·실행 확인)
- 폰 release 주소 가드: 11/07 JBR 21의 `:phone:assembleRelease --dry-run --offline --no-daemon --console=plain`에서 주소 누락 오류·종료 코드 1을 확인했습니다. 예상한 설정 차단이며 release APK를 만든 결과는 아닙니다.
- 제출용 release APK: 아직 없음. 예정 경로는 `apk/팀명_마중_release.apk`, 폰 앱은 `apk/팀명_마중_phone_release.apk`입니다. 팀명·배포 URL·서명 설정 확정 후 생성합니다.
- 다음 작업: 폰 앱 P3/P4, 배포 검증 S4~S5, 실제 발화 확인 A6, 통합·제출 D1~D5. P1/P2는 완료했습니다. 세부 상태와 완료 기준은 [TASKS.md](docs/TASKS.md)에 있습니다.

설계 원칙은 [DESIGN.md](docs/DESIGN.md), 통신 규격은 [PROTOCOL.md](docs/PROTOCOL.md), 서버는 [SERVER.md](docs/SERVER.md), 차 앱은 [CAR_APP.md](docs/CAR_APP.md)에 있습니다.

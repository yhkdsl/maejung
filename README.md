# 마중

현대자동차그룹 차량용 앱 개발 공모전(Pleos App Market) 출품작. 픽업 요청을 승인하면 서로의 위치와 ETA를 공유하고, 가까이 오면 실내등으로 차를 찾게 하며, 승객 문 신호로 탑승을 감지해 공유를 끝내는 차량 앱입니다.

## 구현 상태

2026-11-07, P3 완료 커밋 `f5c7502`를 기준으로 **P4 위치·서비스·실제 지도 구현과 합의한 debug 범위의 main QA를 완료**했습니다. 최종 전체 실행은 phone 184 + app 50 + protocol 9 = **243개 통과**(실패·오류·건너뜀 0), lint 오류 0·경고 12개입니다. 마지막 상수 정리 후 증분 실행·최신 APK 설치와 권한 안내·양쪽 지도·이동·탑승·알림 종료·app_closed 서버 종료도 확인했습니다. 실행별 근거와 검증 경계는 [PHONE_APP.md 8절](docs/PHONE_APP.md#8-p4-검증-결과)에 기록합니다. 독립 검토 결과는 최종 QA 근거와 함께 관리합니다.

**P3 검증 이력**: 응답 기한 수정 후 phone 111개와 직전 새 실행의 변경 없는 app 50개·protocol 9개로 자동 검증 합계는 170개입니다(실패·오류·건너뜀 0). 폰 debug 조립·lint는 성공했으며 lint는 오류 0·경고 10개입니다. 실제 페어링·요청·수락·직접 종료·거절·상태 복원·4401·4409·서버 재시작·미리보기 격리와 당시 최종 APK 정상 흐름을 확인했습니다. 이 수치는 P4 결과가 아닙니다.

- **차 앱 구현**: 가족 연결 코드, 헤드업 요청 알림·수락·거절, Pleos TTS/STT, 위치·ETA 공유, OpenStreetMap 지도, 500m 이상일 때 길안내 시작, 근접 시 실내등 제어, 탑승 감지·공유 종료.
- **서버 구현**: 차 등록, 코드 페어링, 서명 토큰, WebSocket 중계, 세션 만료. DB 없이 세션·연결 정보를 메모리에 두며 좌표는 수신 즉시 상대에게 중계합니다.
- **검증 대기**: 실제 발화로 음성 승인·거절, Docker 이미지 빌드, Railway 배포.
- **폰 앱 P3·P4 완료**: 실제 REST·WebSocket·자격 보관·세션 복원 위에 Sharing 전용 `LocationManager.GPS_PROVIDER`, 위치 포그라운드 서비스, osmdroid 실제 지도를 연결했습니다. 마지막 실제 GPS fix를 정지 중에도 3초마다 전송하고, 지도 아래 HUD는 GPS 직선거리·이동 기준 방향·수신 ETA를 표시합니다. GMS 위치 의존성은 없습니다. P2 도식은 debug 미리보기로 분리했습니다. P3 근거는 [7절](docs/PHONE_APP.md#7-p3-검증-결과), P4 결과는 [8절](docs/PHONE_APP.md#8-p4-검증-결과)에 있습니다. 실물 기기·release APK 런타임·API 26 설치/런타임은 미검증입니다.
- **제출 준비 중**: 서명된 release APK, 통합 데모 스크립트·영상, 제출용 기획서, GitHub 게시.

## 구성

| 폴더 | 내용 |
|---|---|
| `app/` | 차 앱 (Pleos Connect, Android Automotive) |
| `phone/` | 탑승자 폰 앱 (P2 debug 미리보기, P3 연결·P4 위치·지도 debug 검증 완료) |
| `protocol/` | 공용 REST·WebSocket 메시지 모델 (Kotlin/JVM) |
| `server/` | 중계 서버 (독립 Gradle 프로젝트, Kotlin, Spring Boot, WebSocket) |
| `docs/` | 설계 문서 |
| `scripts/` | 가짜 탑승자 클라이언트, 화면 조작·로그 확인 도구 |

## 개발·테스트 환경

- JDK 21. macOS에서는 Android Studio 번들 JBR을 사용합니다.
- 차 앱: compileSdk 35, targetSdk 34, minSdk 28. Android SDK 경로는 Android Studio 또는 루트 `local.properties`의 `sdk.dir`로 설정합니다.
- 폰 앱: compileSdk 35, targetSdk 34, minSdk 26, JVM 17. Compose BOM `2025.05.01`·OkHttp `4.12.0`·osmdroid `6.1.20`을 사용합니다. P4는 위치·알림·위치 포그라운드 서비스 권한을 추가하며 위치원은 차와 같은 Android LocationManager입니다.
- 검증에 사용한 차 AVD: Pleos Connect Emulator v2.1, `Pleos_Connect_v2_1` (API 34, arm64). 일반 폰 에뮬레이터에서는 Pleos 차량 SDK 기능을 검증할 수 없습니다.
- 폰 AVD: 기존 `Medium_Phone_API_36.0` (API 36, Google APIs Play Store, arm64). 11/07 추가 이미지 다운로드 없이 차 AVD와 동시 부팅했고 `adb devices`의 두 device와 폰 `sys.boot_completed=1`을 확인했습니다(P1 완료). 폰 `emulator-5556`에서 P2 debug APK 설치·실행·화면을 확인했습니다.
- 차·폰 debug 서버 주소는 `http://10.0.2.2:8080`입니다. 호스트에서는 `http://localhost:8080`을 사용합니다. 폰의 HTTP 예외는 debug 로컬 호스트로 한정하며 release에는 HTTPS 주소가 필요합니다.
- 가짜 탑승자 스크립트는 내장 WebSocket을 지원하는 Node.js 22 이상이 필요합니다.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :protocol:test :app:testDebugUnitTest :app:assembleDebug
```

**P2 당시 검증 이력**: 11/07 JBR 21로 폰 16개 테스트 통과·debug 조립 성공, `--rerun-tasks --offline --no-daemon --console=plain`으로 위 차·공용 명령을 실행해 차 앱 46 + 공용 모듈 9 = 55개 통과(실패·오류·건너뜀 0), 차 debug 조립 성공을 확인했습니다. 서버 소스는 변경하지 않아 P2에서 서버 테스트는 재실행하지 않았고 이전 34개 결과를 유지합니다. 이전 전체 89개 결과에 폰 16개가 포함된 것은 아닙니다. 당시 폰 lint는 오류 0·경고 8개(targetSdk 34, 고정 의존성 5건, dataExtractionRules, 아이콘 누락)였습니다. 이후 P2 UI 재설계의 24개 결과는 PHONE_APP.md 6절, P3의 새 결과는 7절로 구분합니다. 실제 발화 STT는 아직 확인하지 않았습니다.

## 서버

### 로컬 실행

JDK 21이 필요합니다.

```bash
cd server
./gradlew test       # 단위·통합 테스트
./gradlew bootRun    # http://localhost:8080
```

서버가 실행되면 다른 터미널에서 `curl localhost:8080/health`로 확인합니다(`{"status":"ok"}`).

서버를 종료할 때는 `bootRun`을 실행한 터미널에서 Ctrl-C를 사용합니다. 8080 포트를 사용하는 프로세스를 일괄 종료하면 차 에뮬레이터까지 꺼질 수 있습니다.

### 차 앱과 가짜 탑승자 실행 (별도 개발용 대역)

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

### 폰 앱 실제 연결과 위치 공유 (P3/P4)

로컬 서버와 실제 서버 모드의 차 앱을 먼저 실행합니다. 다음은 폰 debug APK의 빌드·설치·실행 절차입니다. P3 실행 이력은 PHONE_APP.md 7절, P4 실행 결과는 8절입니다.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :phone:testDebugUnitTest :phone:assembleDebug :phone:lintDebug
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.0 -port 5556 -no-snapshot-load &
adb devices -l
adb -s emulator-5556 install -r phone/build/outputs/apk/debug/phone-debug.apk
adb -s emulator-5556 shell am start -n com.maejung.phone/.MainActivity
```

이미 켜져 있는 AVD는 다시 실행할 필요가 없습니다. serial은 `adb devices` 결과에 맞춰 바꿉니다(11/07 동시 부팅: 차 `emulator-5554`, 폰 `emulator-5556`). 설치·실행·로그·위치 주입 등 각 `adb` 명령에 `-s`로 대상 기기를 지정합니다.

1. 차 화면에서 **가족 연결**을 눌러 받은 6자리 코드를 폰의 **차량 연결 코드**에 넣고 이름을 입력합니다. 이름은 앞뒤 공백 제거 후 Kotlin `String.length` 1~20, 코드는 ASCII 숫자(`0`~`9`) 정확히 6자리입니다.
2. 폰에서 **연결하기**를 누르면 `POST /api/pairings`를 호출하고 자격을 보관한 뒤 WebSocket에 연결합니다. 실패 시 입력을 유지해 수정할 수 있습니다.
3. 연결과 첫 `session.state` 동기화가 끝나면 **데리러 와줘**를 누릅니다. 이때 fine·coarse를 함께 요청하며 coarse만 허용해도 Sharing·서비스 실행 자격은 유효합니다. 거부해도 픽업 요청은 보내고 위치 공유를 할 수 없다는 안내를 표시합니다. 이 API 36 에뮬레이터의 coarse-only는 지연 후 실제 난독화 GPS를 받았습니다. 정확한 이동에는 fine이 필요하며 실제 fix 전에는 위치를 만들어 보내지 않습니다. 차에 요청이 표시되면 차에서 수락하거나 거절합니다.
4. Sharing에서 위치 권한이 있으면 위치 서비스와 **엄마 차와 위치 공유 중** 알림을 사용합니다. API 34에서 앱이 뒤에 있는 동안 수락을 받으면 위치 FGS를 자동 시작하지 않고 화면 복귀 후 재시도합니다. 이미 시작한 Sharing 서비스는 백그라운드·화면 꺼짐에도 GPS를 유지합니다. 대략적 위치의 정밀도 안내와 알림 차단 안내는 첫 fix 전부터 독립적으로 표시합니다. 최신 APK에서 두 안내와 권한 거부 시 가려지지 않는 실제 차 마커·미확인 HUD·위치 FGS 없음을 확인했습니다.
5. **마중 끝내기**와 확인창의 같은 버튼, 또는 공유 알림의 **끝내기**로 종료합니다. 종료 안내는 3초 후 홈으로 돌아갑니다. 연결이 끊겨 종료를 보내지 못하면 활성 상태와 재시도 안내를 유지하므로, 다시 연결한 뒤 종료합니다.
6. 앱을 다시 실행하면 보관한 토큰·이름으로 연결하고 서버 상태를 복원합니다. 일반 끊김은 자동 재연결하며, 4401은 자격을 지우고 연결 화면으로, 4409는 자격을 유지한 채 **다시 연결**을 기다립니다. 두 종료 코드에서는 위치 서비스·GPS·heartbeat도 중단합니다.

요청 응답의 최초 10초 기한은 일반 끊김·재연결에도 유지합니다. 만료되면 연결 안내와 함께 홈으로 돌아가 새 요청을 막고, 서버의 새 `session.state`로 기존 요청·공유·없음 상태를 확인합니다. 4401·4409 뒤에는 자동 재접속하지 않습니다.

P4의 `SharingMap`은 osmdroid 실제 지도에 양쪽과 동일한 WGS84 좌표를 차 네이비 `#002C5F`·탑승자 시안 `#00AAD2`로 표시합니다. User-Agent·출처 표시·지도 클리핑을 적용하고 지도 아래 HUD에 내 GPS→차 직선거리, GPS 이동 방향 기준 화살표, `peer.eta`로 받은 도착예상만 표시합니다. 값이 없으면 `—`이며 좌표 숫자·지도 준비 문구·샘플 값으로 대체하지 않습니다. 폰 위치 주입 예는 `adb -s emulator-5556 emu geo fix 127.06280 37.49830`입니다. main은 같은 fix의 양쪽 지도·정지 2분 초과 heartbeat·실제 폰 GPS 이동·근접·RR 탑승 종료를 확인했습니다. 상세 근거와 검증 경계는 PHONE_APP.md 8절에 있습니다.

실행 중인 공유 서비스가 최근 앱 제거의 `onTaskRemoved`를 받으면 `appClosed()`로 `session.end(app_closed)`를 최선 노력으로 보내고 GPS·소켓 재시도를 중단합니다. 현재 서버는 이를 `manual`로 알립니다. 강제 종료·Android 작업 관리자의 중지는 콜백을 보장하지 않고 오프라인 종료도 큐에 남기지 않습니다. 위치 권한이 없거나 요청 대기 중이면 위치 FGS가 없으므로 스와이프 종료를 보장하지 않습니다. 미전송 시 서버의 공유 heartbeat 2분 초과 만료가 대안입니다([PHONE_APP.md 5절](docs/PHONE_APP.md#5-p4-위치-전송과-앱-종료)).

### 폰 앱 오프라인 미리보기 (P2 유지)

debug의 실제 연결 화면 우측 상단 메뉴에서 **미리보기 열기**로 명시적으로 진입합니다. 미리보기 안에서는 **입력 확인 후 홈 미리보기** → **데리러 와줘 · 미리보기**로 요청 대기 예시를 보고, 상단 **오프라인 미리보기 메뉴**에서 공유·정보 없음·근접·종료 6가지를 고릅니다. **실제 연결 화면으로**를 눌러 실제 화면에 복귀합니다. 실제 페어링 중이거나 요청·공유 중에는 미리보기 진입을 막습니다.

미리보기의 연결·요청·실내등 표시는 로컬 샘플이며 실제 차에 요청·종료 메시지를 보내지 않습니다. 저장된 P2 장면이 있어도 앱 시작은 실제 화면입니다. 미리보기 자체에는 서버가 필요 없지만 Application의 실제 연결 수명은 별도로 유지됩니다. release는 미리보기 메뉴 없이 실제 연결을 사용하며, release APK·런타임은 미검증입니다.

P2 당시 화면 QA와 회전·키보드·프로세스 재생성 기록은 [PHONE_APP.md 6절](docs/PHONE_APP.md#6-p1p2-검증-이력과-ui-재설계-검증-상태)에 보존합니다. P3의 새 실행 근거는 같은 문서 7절에 기록합니다.

### Railway 배포

1. Railway 프로젝트에 서비스를 추가하고 이 저장소를 연결합니다.
2. **Root Directory**를 `server`로 지정합니다. `server/Dockerfile`로 빌드됩니다.
3. 환경변수를 넣습니다.
   - `SPRING_PROFILES_ACTIVE=railway`
   - `MAEJUNG_TOKEN_SECRET`: 32자 이상 임의 문자열 (예: `openssl rand -base64 48`). 바꾸면 모든 차·폰 연결이 무효가 됩니다.
4. 헬스체크 경로를 `/health`로 지정합니다.
5. 배포 URL을 차·폰 앱 release 빌드의 `maejung.serverUrl`에 넣습니다(`local.properties` 또는 `-Pmaejung.serverUrl=https://...`). 주소가 없으면 release 빌드가 실패하며 폰은 HTTPS 주소를 요구합니다. 폰도 해당 주소로 실제 REST·WebSocket을 사용합니다. Railway에서의 양쪽 앱 런타임은 아직 검증하지 않았습니다.

현재 배포 설정은 작성됐지만 Docker 이미지 빌드 성공과 Railway 배포는 아직 검증하지 않았습니다. release 서명 설정도 남아 있습니다.

## APK와 남은 작업

- 차 앱 debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- 폰 앱 debug APK: `phone/build/outputs/apk/debug/phone-debug.apk` (최신 설치 Success·main P4 QA 완료. P3 이력은 PHONE_APP.md 7절, P4 근거는 8절)
- 폰 release 주소 가드의 P2 이력: 11/07 JBR 21의 `:phone:assembleRelease --dry-run --offline --no-daemon --console=plain`에서 주소 누락 오류·종료 코드 1을 확인했습니다. 예상한 설정 차단이며 P3 HTTPS 설정·release APK 런타임 검증을 대신하지 않습니다.
- 제출용 release APK: 아직 없음. 예정 경로는 `apk/팀명_마중_release.apk`, 폰 앱은 `apk/팀명_마중_phone_release.apk`입니다. 팀명·배포 URL·서명 설정 확정 후 생성합니다.
- 다음 작업: 배포 검증 S4~S5, 실제 발화 확인 A6, 통합·제출 D1~D5. P1/P2/P3/P4는 완료했습니다. 세부 상태와 완료 기준은 [TASKS.md](docs/TASKS.md)에 있습니다.

설계 원칙은 [DESIGN.md](docs/DESIGN.md), 통신 규격은 [PROTOCOL.md](docs/PROTOCOL.md), 서버는 [SERVER.md](docs/SERVER.md), 차 앱은 [CAR_APP.md](docs/CAR_APP.md)에 있습니다.

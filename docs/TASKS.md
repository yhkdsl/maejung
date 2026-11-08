# 마중 작업 목록

에이전트는 이 목록을 위에서부터 집어 간다. 각 작업은 **완료 기준을 모두 만족해야** 끝난 것이다. 끝나면 체크하고, 결정·검증 결과를 관련 설계 문서에 반영한다.

**현황 기준: 2026-11-08, HEAD `d5bdcc5`**. P4는 `3c0b427`, 차의 좌표 겹침 지도 수정은 `d5bdcc5`에 커밋됐다. **D1 실제 차·폰 데모 재현, S4 Docker 이미지 빌드·컨테이너 health 확인, D4 촬영 대본을 완료했다.** 영상 녹화·편집은 사용자 작업으로 남아 있다. P4의 243개 테스트와 debug QA는 기존 완료 이력이며 PHONE_APP.md 8절에 보존한다. 이번 제출 준비에서는 앱 소스를 변경하지 않았고 차·폰 debug 조립과 실제 시연을 확인했다. A6 실제 발화와 S5 Railway 배포는 미완료다.

- 1차 제출 마감: **2026-11-15(일)**, 목표 11/14. 2차 PT: 12/3~4.
- 기존 P2 검증 이력(UI 재설계 전): phone 테스트 16개·debug 조립 성공(7초), 메인 JBR 21의 `:protocol:test :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --offline --no-daemon --console=plain` 성공(12초·47개 태스크 실행), app 46 + protocol 9 = **55개 통과**, 실패·오류·건너뜀 0. 서버는 소스 변경 없이 미재실행(기존 34개). 폰 lint 온라인 성공(28초·오류 0·경고 8), 폰 AVD 설치·화면·입력·회전·프로세스 재생성 검증은 PHONE_APP.md 6절에 정리했다.
- UI 재설계 최종 검증(메인 제공): `:phone:testDebugUnitTest :phone:assembleDebug :phone:lintDebug` 오프라인 통합 실행 **성공(19초·55개 태스크: 실행 14·최신 상태 41)**. phone **24개 모두 재실행·통과**(기존 16 + 새 `SharingCompassTest` 8, XML `2026-11-07T12:31:06Z`·실패/오류/건너뜀 0), lint 오류 0·기존 경고 8개, 최종 debug APK 설치 Success. API 27 테마 분리(기본 리소스·`values-v27`)와 불필요한 null 검사 제거를 반영했다. 독립 소스·시각 검토와 네이티브 QA는 PASS이며 PNG 근거는 PHONE_APP.md 6절이다. API 26 설치·런타임은 미검증이다.
- 설계 문서: [DESIGN.md](DESIGN.md)(원칙·탑승 판정·실내등·SDK), [PROTOCOL.md](PROTOCOL.md)(통신 계약), [SERVER.md](SERVER.md), [CAR_APP.md](CAR_APP.md), [PHONE_APP.md](PHONE_APP.md)
- **담당이 "사용자"인 작업은 에이전트가 하지 않는다.** 계정 생성, 결제, 외부 서비스 설정, 대용량 다운로드 승인은 사용자 몫이다.
- **커밋은 사용자가 요청할 때만 한다.** 형식은 루트 `CLAUDE.md` "처음 온 에이전트가 할 일" 4번.
- 의존 관계: 0절은 언제든 먼저. S1→S2→S3. M1 → A1 → A2 → A3·A4 → A5(S3 필요) → A6·A7·A8. P1 → P2(M1 필요) → P3(S3 필요) → P4. D는 A·P·S가 끝난 뒤.
- **병렬 진행**: M1이 끝나면 차 앱(A)과 폰 앱(P)은 서로 파일이 겹치지 않아 세션을 나눠 동시에 할 수 있다. 공용 파일(`protocol/`, 루트 `settings.gradle.kts`·`build.gradle.kts`)은 한쪽만 고치고, 바꿀 일이 생기면 다른 쪽에 알린다.

## 일정 (완료 실적과 남은 목표, 제출 목표 11/14)

| 날짜 | 에이전트 | 사용자 |
|---|---|---|
| 11/6(금) ✅ | 컨셉 확정, 통신·탑승 신호 확인, 문서 작성 | - |
| 11/7(토) 완료 | 사전 확인 C1·N1·V1, 서버 S1~S3, 공용 M1, 차 앱 A1~A9 구현·지도, 폰 P1/P2/P3와 P4 debug 범위 main QA 완료 | 실제 발화 확인은 대기 |
| 11/8(일) 완료 | D1 실제 차·폰 시연, S4 Docker 빌드·health·컨테이너 정리, D4 150초 촬영 대본 | 시연 영상 녹화·편집은 대기 |
| 11/9~12(월~목) 목표 | 통합 제출 준비 | Railway 배포(S5), 팀명(U1), 접수양식(U2), 음성 실제 발화(A6) |
| 11/13(금) | D2, D3, 기획서 최종본 정리 | 시연 영상 녹화(D4) |
| 11/14(토) | 최종 점검 | GitHub 생성(D5), 기획서·개인정보 동의서 제출 |

## 사용자가 할 일 (모아 보기)

에이전트는 아래 일을 대신하지 않는다. 필요한 시점이 되면 사용자에게 알리고 기다린다.

| ID | 할 일 | 필요한 시점 | 상태 |
|---|---|---|---|
| U1 | 팀명 정하기 (APK 이름, 제출 메일 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`) | D3 전 | 대기 |
| U2 | 공식 접수양식(DOCX) 확인. 기획서를 양식 목차에 맞춰 재구성해야 한다. 사용자가 이전에 다운로드를 거절했으니 에이전트가 임의로 받지 않는다 | 기획서 최종본 전 | 대기 |
| S5 | Railway에 서버 배포, 배포 URL 전달 (README "Railway 배포") | A5 이후 아무 때나, D3 전 필수 | 대기 |
| P1 | 기존 폰 에뮬레이터 이미지로 차·폰 동시 실행 확인 | P2 전 | 11/07 완료, 추가 다운로드 없음 |
| A6 | 음성 인식 테스트 때 PC 마이크에 승인·거절 발화하기("응"/"아니") | A6 완료 전 | 대기 |
| D4 | 시연 영상 녹화·편집 | 11/13 | 대기(촬영 대본 11/08 완료) |
| D5 | GitHub 저장소 생성과 푸시 승인 | 11/14 | 대기 |
| - | 기획서 제출(메일), 개인정보 동의서 서명 | 11/14 | 대기 |

## 0. 확인 작업 (결과에 따라 설계가 바뀐다)

11/07 사전 조사는 끝났다. 현재 구현과 실제 발화 검증 상태는 아래와 같으며 상세는 DESIGN.md 6·7절이다.

- [x] **C1. 차 에뮬레이터 위치 주입** — `adb emu geo fix`가 `LocationManager`로 들어오는 것을 확인했다. 현재 `AndroidLocationSource`를 사용하며 `FixedLocationSource` 구현은 없다.
  - 재현: 위치 권한 허용 후 `adb -s emulator-5554 emu geo fix 127.06030 37.49950`(경도, 위도 순서). 앱 차 위치·로그 확인. 에뮬레이터 앱 사용자는 10이다.
- [x] **N1. NaviHelper 길안내·ETA 조사** — 경로가 있을 때 `onDrivingInfo`로 남은 거리·시간을 받는다. A8에서 수락 후 최초 거리 500m 이상이면 탑승자 좌표로 길안내를 시작하고, 종료 시 취소하도록 구현했다. A7은 내비 값이 없으면 직선거리 기반 추정값을 사용한다.
- [x] **V1. 한국어 음성 사전 조사** — Android 기본 TTS는 엔진이 없고 Android SpeechRecognizer는 준비 콜백 없이 시간 초과였다. 현재 말하기·듣기 모두 Pleos SDK ON_DEVICE로 구현했다. TTS 재생과 STT 인식 준비까지 확인했고, 실제 발화 확인은 A6에 남긴다.

## 1. 서버 (`server/`)

- [x] **S1. 프로젝트 생성** — SERVER.md 2~5절대로 독립 Gradle 프로젝트, wrapper 8.12, Dockerfile(Eventoday와 같은 2단계), `application.yml`(포트·비밀키 기본값), `server/.gitignore`(build/, .gradle/).
  - 완료 기준: `cd server && ./gradlew test` 성공, `./gradlew bootRun` 후 `curl localhost:8080/health` → `{"status":"ok"}`.
- [x] **S2. 토큰·REST** — TokenService, PairingCodeStore, `/api/vehicles`, `/api/pairing-codes`, `/api/pairings`, 오류 형식(PROTOCOL.md 2절).
  - 완료 기준: `TokenServiceTest`, `PairingCodeStoreTest` 통과. curl로 등록 → 코드 → 페어링 성공, 토큰 없이 코드 발급 시 `401 INVALID_TOKEN`.
- [x] **S3. WebSocket과 세션** — ConnectionRegistry, MaejungWebSocketHandler, SessionService, SessionSweeper.
  - 완료 기준: SERVER.md 6절의 `SessionServiceTest`, `WebSocketFlowTest`(①~⑤) 통과.
- [x] **S4. 배포 준비·빌드 검증** — 11/08 `docker build -t maejung-server server` 성공(`bootJar` 49초, 4개 태스크 실행). 이미지로 검증용 컨테이너 `maejung-s4-20261108-0105`를 `127.0.0.1:18080:8080`에 띄워 `/health`의 `{"status":"ok"}`를 확인한 뒤 컨테이너만 중지·제거했다. 기존 8080 서버와 에뮬레이터는 유지했다. Dockerfile·Railway 설정 안내(Root Directory `server`, `SPRING_PROFILES_ACTIVE=railway`·`MAEJUNG_TOKEN_SECRET`, `/health`)는 작성돼 있으며 실제 Railway 배포는 S5에 남아 있다.
  - 완료 기준: `docker build -t maejung-server server` 성공(Docker가 없으면 사용자에게 알리고 건너뛴다).
- [ ] **S5. Railway 배포** — 담당: **사용자**. 서비스 생성, 환경변수 입력, 배포 URL을 알려 준다. 에이전트는 받은 URL을 `local.properties`의 `maejung.serverUrl`에 넣는 방법만 안내한다.

## 2. 공용 모듈

- [x] **M1. `protocol/` 모듈** — PROTOCOL.md 3절의 모든 메시지를 `@Serializable` 클래스로. 루트 플러그인 정리와 JVM 17 설정은 CAR_APP.md 1절. 11/07 완료(테스트 9개, 서버 실제 출력 형식 디코딩 포함). 사용법은 CAR_APP.md 1절.
  - 완료 기준: 메시지마다 직렬화 왕복 단위 테스트(`./gradlew :protocol:test`). 모르는 type은 예외 없이 `Unknown`으로 디코딩.

## 3. 차 앱 (`app/`)

- [x] **A1. 골격** — compileSdk 35, Compose 도입, `MaejungApp`·`MainActivity`(런처, distractionOptimized), 스파이크는 런처에서 빼고 adb로만 열기, CAR_APP.md 2절 인터페이스(`VehicleSignals`, `SessionTransport`, `LocationSource`, `VoiceIO`), debug 서버 주소와 평문 허용(10.0.2.2), release 서버 주소 없으면 빌드 실패. 11/07 완료. 공조·창문 제어 퍼미션 정리는 스파이크가 쓰고 있어 D3(릴리스) 때 한다.
  - 완료 기준: `./gradlew :app:assembleDebug` 성공, 에뮬레이터에서 HomeScreen이 뜸.
- [x] **A2. 상태 머신** — `CarSessionMachine`(CAR_APP.md 3.4 표 전부). 11/07 완료(테스트 38개 + 조사·음성 답 3개).
  - 완료 기준: 표의 모든 행 + 아래 경우의 단위 테스트 통과(`./gradlew :app:testDebugUnitTest`): 길안내 500m 경계(이상이면 한 번만 시작, 미만이면 안 함, 종료 시 취소), 50m 진입·70m 이탈 경계, Nearby 전에 열린 문 무시, 실내등 원래 ON·AUTOMATIC(복구 후 원래 값), `supportsOn=false`(실내등 효과·`vehicle.light` 없음), 종료 전이에서는 `vehicle.light`를 보내지 않음(Nearby→Sharing에서만 보냄), `lightBefore`가 이미 있으면 덮어쓰지 않음, `session.state none`으로 정리.
- [x] **A3. 차량 신호** (11/07 완료: 기어 P 수신, 승객 문 3개 열림·닫힘 구독, 실내등 지원 여부·현재 값 조회와 켜기·복구를 앱 흐름에서 확인) — `PleosVehicleSignals`(기어·승객 문 구독, 실내등 캐퍼빌리티·현재 값 조회·set).
  - 완료 기준: Virtual Console로 기어·문을 바꾸면 화면(디버그 표시)에 반영, 실내등 ON/OFF 후 조회값 확인. 실내등 스위치는 구독하지 않고 앱 시작·Sharing 진입 때 조회한다(CAR_APP.md 3.2).
  - Nearby 도중 앱을 강제 종료(`adb shell am force-stop com.maejung`)하고 다시 켜도 실내등이 원래 값으로 돌아오는지(Prefs의 `lightBefore`) A5에서 확인한다.
- [x] **A4. 가짜 서버로 한 바퀴** (11/07 완료: 홈 화면 "가짜 요청 받기" → 수락 → 탑승자가 학원에서 2초 간격 15단계로 접근 → 34m에서 Nearby·실내등 ON → Virtual Console RR 문 열고 닫기 → Ended(boarded)·실내등 OFF 복구 → 3초 뒤 Idle. 로그 `adb logcat -s CarSession`) — `FakeSessionTransport` + D1 좌표로 요청 → 승인 → 접근 → 실내등 → 문 열고 닫기 → boarded.
  - 완료 기준: 서버 없이 에뮬레이터에서 전체 흐름이 보이고, 끝나면 실내등이 원래 값으로 돌아감.
- [x] **A5. 실제 서버 연결** — `WebSocketTransport`, `ApiClient`, 차 등록·토큰 저장, 가족 연결 코드 화면, `session.state` 처리. 11/07 완료: 로컬 서버 + `scripts/fake-rider.mjs`(폰 대신)로 코드 발급 → 페어링(차에 "민지 연결됨") → 요청 → 수락 → 위치 양방향 → 34m Nearby·실내등 ON·폰에 `peer.light on` → RR 문 → 양쪽 `session.ended(boarded)`. Nearby 중 서버를 껐다 켜자 차가 다시 붙어 `session.state none` → Ended(peer_lost)·실내등 OFF 복구. 차 토큰은 Prefs에 남아 재등록 없음.
  - 완료 기준: 로컬 서버에 붙어 코드 발급. 서버를 껐다 켜면 재연결 후 `session.state none`을 받아 진행 중 세션이 Ended(peer_lost)로 정리되고 실내등이 복구됨.
- [ ] **A6. 음성 — 구현 완료, 실제 발화 검증 대기** — `PleosVoiceIO`(말하기·듣기 모두 Pleos Speech SDK ON_DEVICE). 11/07 TTS 안내 재생과 Pleos STT의 "말할 준비 됨"까지 확인했다. 승인·거절 문장 파싱도 구현됐지만 **사람 발화로 승인·거절되는지 확인해야 한다(사용자 협조 필요).**
  - 완료 기준: 요청 시 TTS 안내 후 실제 "응"으로 승인, "아니"로 거절됨을 확인한다. 인식 실패·시간 초과 때 버튼으로 답할 수 있어야 한다.
- [x] **A8. 알림·길안내·백그라운드** — `PickupNotifications`(스파이크 `spike/PickupAlert.kt` 참고), `PickupAlertReceiver`, `PickupNavigator`, `CarSessionService`(CAR_APP.md 2절 규칙). 11/07 완료(음성 승인 제외): 내비가 앞에 있는 상태에서 헤드업 요청 알림 → 수락(헤드업·알림 센터 둘 다) → 내비를 띄운 채로 위치 공유·34m 근접 안내·실내등·탑승 감지까지 동작. 차를 1,099m 떨어뜨리면 수락 직후 길안내 시작, 차에서 "공유 끝내기"하면 길안내 취소. **음성 승인은 미확인**: A6에서 Pleos STT로 바꿔 인식 준비까지는 확인, 실제 발화 확인이 남았다.
  - 완료 기준: 내비 화면이 앞에 있을 때 요청이 오면 헤드업 알림이 뜨고 수락·거절이 동작. 차와 탑승자가 500m 이상이면 수락 후 길안내가 시작되고, 탑승 감지·수동 종료 시 길안내가 취소됨. 마중 앱이 화면 뒤에 있어도 위치 전송과 탑승 감지가 계속됨.
  - 음성 승인 테스트는 실제 발화가 필요하다. 에뮬레이터에서 PC 마이크를 켜고 사용자에게 "응"이라고 말해 달라고 요청한다.
- [x] **A7. 위치·ETA** — `AndroidLocationSource`(LocationManager), 3초 간격 마지막 위치 heartbeat, `EtaCalculator`(내비 값 우선·없으면 추정), `eta.update` 전송 규칙. 11/07 완료: 접근할수록 가짜 탑승자 클라이언트에 `peer.eta` 60→40→28→16초, 정차 3분 넘게 유지해도 끊기지 않고 30초마다 ETA 재전송. `EtaCalculatorTest` 5개. 방향은 8방위 글자로 표시한다. 현재 화면은 A9 기준이다.
  - 완료 기준: 정차 상태로 3분 두어도 서버가 `peer_lost`로 끊지 않음. 탑승자 위치를 옮기면 SharingScreen의 거리·방향·ETA가 바뀜. 서버 로그에 `eta.update`가 30초 간격으로 찍힘.
- [x] **A9. 공유 화면 지도** — 11/07 `74821e3`에 구현. `PickupMap`은 osmdroid 6.1.20 + OpenStreetMap 타일로 차(검정)·탑승자(파랑)·차 주변 50m 원을 표시하고 두 점이 보이도록 화면을 맞춘다. 지도는 왼쪽, 거리·8방위·ETA·종료 버튼은 오른쪽이다. 5m 미만은 방향을 숨기고 30초 미만 ETA는 "곧 도착"으로 표시한다.
  - 검증 기록: 에뮬레이터 지도 표시, 근접 흐름, 재설치 후 재진입 확인. 코드에는 앱 식별 User-Agent와 출처 표시가 있다.
- [x] **A10. 대기 중 혜택 데모 화면** — 11/08, 1차 영상에 넣기로 해서 추가. `ui/WaitingOffers.kt`가 Sharing + 기어 P에서만 지어낸 혜택 2개와 "제휴 예시"를 공유 화면 오른쪽에 보여주고, P 이탈·Nearby에서는 숨긴다. 광고 SDK·서버 통신·누르는 곳은 없다. 상세는 CAR_APP.md 4절.
  - 완료 기준: `WaitingOffersTest` 3개 통과(차 앱 53개), 에뮬레이터에서 P·Sharing 표시와 Nearby 숨김 확인(11/08 확인).

## 4. 폰 앱 (`phone/`)

- [x] **P1. 폰 에뮬레이터** — 11/07 기존 `Medium_Phone_API_36.0`(API 36, Google APIs Play Store, arm64)과 설치된 이미지를 확인해 추가 다운로드 없이 부팅했다. `adb devices -l`에서 차 `emulator-5554`와 폰 `emulator-5556`이 동시에 device, 폰 `sys.boot_completed=1` 확인. AVD 이름·실행 방법은 루트 CLAUDE.md에 반영했다. 새 이미지 다운로드가 필요해지면 사용자에게 먼저 묻는다.
  - 완료 기준: 차 에뮬레이터와 동시에 실행되고 `adb devices`에 둘 다 보임. AVD 이름과 실행 방법을 루트 CLAUDE.md "에뮬레이터 실행"에 추가.
- [x] **P2. 모듈과 화면** — 11/07 `:phone` 설정·Manifest와 PHONE_APP.md 2~3절의 연결·홈·마중 중·요청 대기·데이터 없음·근접·종료 6가지의 기존 오프라인 미리보기 구현·검증 완료. 승인 UI 재설계의 새 최종 검증은 아래 별도로 기록한다. `BuildConfig.DEBUG`로 제한하고 release는 P3 전까지 연결·요청 불가 안내. 실제 네트워크·토큰 보관·GPS·권한·서비스는 포함하지 않는다.
  - 완료 기준: `./gradlew :phone:assembleDebug` 성공, 폰 에뮬레이터에서 세 화면과 종료 문구 6가지가 보임.
  - 기존 검증 이력(UI 재설계 전): phone 16개 테스트·debug 조립, 폰 `emulator-5556` 설치·런처 실행, 세 화면·요청 대기·정보 없음·근접·종료 6가지의 실제 표시와 긴 문구 줄바꿈, 빈 입력·코드 오류·키보드 스크롤·종료 확인·회전·백그라운드 프로세스 재생성 후 20m 복원 확인. APK `phone/build/outputs/apk/debug/phone-debug.apk`, 런처 `com.maejung.phone/.MainActivity`. release 차단은 소스·단위 테스트와 URL 가드 dry-run 근거이며 release APK 런타임은 미검증이다.
  - 승인 UI 적용: 네이비/펄/시안 테마·마중 픽셀 브랜드·네이티브 차량 그림·상단 우측 메뉴/작은 오프라인 배지. 코드는 포커스 중 단일 편집기, 포커스 해제 시 6자리 이하 ASCII 숫자를 여섯 칸으로 표시하며 잘못된 원문도 그대로 유지한다. 북쪽 고정 로컬 예시 지도·정차한 차/아이 마커·거리/이동 기준 나침반/ETA HUD·실내등 안내를 구현했다. course 35°·차 방위 80° → 상대 45°, 근접 ETA 약 30초, 정보 없음에서는 모든 위치 마커·방향을 숨긴다. 실제 지도 타일은 미구현이다.
- [x] **P2 UI 재설계 최종 검증** — 메인 제공 최종 빌드·24개 테스트·lint·APK 설치 성공, 독립 리뷰어 Zeno의 소스·시각 검토 PASS. 320dp/글꼴 1.3에서 차 라벨·HUD 여백, 포커스 중 `012345`·커서, 스크롤 종료 버튼을 확인해 필수 지적 2건을 해결했다. `Minji`·`012345` 입력 후 홈 인사, 정보 없음에서 마커·측정값 숨김, 근접 종료 확인의 계속 보기·직접 종료, 가로 근접 지도/HUD/실내등·종료 버튼 스크롤도 네이티브 QA로 확인했다. 캡처는 PHONE_APP.md 6절에 정리했다. 로컬 예시 UI 범위이며 API 26 런타임·release APK 런타임·P3/P4·실제 지도 타일은 포함하지 않는다.
- [x] **P3. 서버 연동** — 실제 REST 페어링, WebSocket 요청, 토큰·이름 영구 보관, 재연결, PHONE_APP.md 4절 상태 전이 구현·검증 완료. 실제 로컬 서버와 차 `emulator-5554`·폰 `emulator-5556`에서 페어링 → 요청 → 차 수락 → 종료, 거절 수신, 자격·요청/공유 복원, 서버 재시작 동기화, 4401 자격 삭제·4409 명시적 재연결, 미리보기 분리를 확인했다. ACK 10초의 원래 기한 유지·늦은 응답 후 스냅샷 복구와 종료 코드 경계 회귀도 통과했다. 폰 111개·차 50개·공용 9개 테스트 실패 0, 폰 APK 조립·설치와 lint 오류 0·경고 10개. 상세 근거는 PHONE_APP.md 7절이다.
  - 계약 변경 순서: PROTOCOL.md 먼저 → protocol 모듈 → server → app → phone. 현재 폰 작업에서 app/·server/ 수정이 필요하면 사용자에게 필요한 변경과 영향을 먼저 알리고 해당 코드는 수정하지 않는다.
  - 완료 기준: 실제 로컬 서버 + 차·폰 에뮬레이터 UI로 페어링 → 요청 → 차에서 승인 → 종료, 별도 요청을 차에서 거절했을 때 "엄마가 지금은 못 온대요"까지 확인. 자격 복원·재연결·초기 `session.state` 동기화와 PHONE_APP.md 4절 전이를 검증한다. GPS·위치 권한·서비스·양쪽 실제 지도 위치 표시는 P4에서 검증한다.
- [x] **P4. 위치 포그라운드 서비스·실제 지도 — debug 범위 main QA 완료**. P3 `f5c7502` 기반으로 Sharing 전용 `LocationManager.GPS_PROVIDER`(GMS 없음)·위치 FGS·osmdroid `6.1.20`을 구현했다. 첫 실제 fix 즉시·이후 정지 중에도 마지막 실제 fix를 3초마다 보내고, 새 세션·종료·4401/4409의 위치·서비스·heartbeat를 정리한다. 기존 wire 계약과 `app/`·`server/`·`protocol/` 소스는 유지한다.
  - 권한·서비스 기준: 픽업 클릭에서 fine·coarse를 함께 요청하며 coarse도 Sharing·FGS 자격에 유효하다. 거부해도 픽업 요청·위치 불가 안내를 유지하고 Requesting·위치 권한 없음에는 위치 FGS를 실행하지 않는다. Sharing ongoing 알림은 "엄마 차와 위치 공유 중"·"끝내기"다. 첫 fix 전부터 대략적 위치의 정밀도 안내와 알림 차단 안내를 독립적으로 표시한다. API 34 백그라운드 수락은 화면 복귀 후 시작·재시도하며 정상 실행 중 Sharing 서비스의 백그라운드 GPS는 계속된다.
  - 지도 기준: User-Agent·`CopyrightOverlay`·`clipToBounds`, 동일 WGS84 차 네이비 `#002C5F`·탑승자 시안 `#00AAD2` 마커. 위치 없음 배너는 양쪽 위치가 모두 없을 때만 표시한다. 지도 아래 HUD는 내 GPS→차 직선거리·유효 GPS course 기준 방향·수신 `peer.eta`만 표시하며 좌표 숫자·지도 준비 문구를 쓰지 않는다. 로컬 도식은 debug `PreviewSharingMap` 전용이다.
  - main QA 근거: 위치 권한 거부 후 실제 요청·Sharing/GPS·FGS 없음·가려지지 않는 차 마커, 정렬된 양쪽 실제 지도·거리/수신 ETA·정지 course `—`, 화면 꺼짐·정지 141.237초 heartbeat, 백그라운드 수락 시 FGS 없음→화면 복귀 후 location FGS·GPS 시작 확인. 실제 폰 GPS 15단계 이동·34m Nearby·실내등 안내·RR 문 탑승 종료/실내등 OFF 복구, 320dp/글꼴 1.3 출처·스크롤 종료 버튼 접근도 확인했다. 최신 공유는 폰 258m·차 257m·양쪽 ETA 약 1분이며 마지막 알림 끝내기도 서버 manual 종료·양쪽 종료 화면·FGS 없음으로 확인했다. 수치·캡처 이름은 PHONE_APP.md 8절, 산출물은 `build/p4-phone-qa/`다.
  - 추가 권한 QA 완료: coarse-only·알림 거부에서도 Sharing·FGS 실행, 두 안내의 독립 표시 확인. API 36 에뮬레이터의 초기 LOW_POWER·10분 간격/GNSS 꺼짐 상태에서는 fix가 지연됐으나 최종 캡처에서 실제 난독화 coarse GPS와 화면 1286m를 확인했다. 영구 수신 불가가 아니며 적시·정확한 이동에는 fine이 필요하다. 실제 fix 전에는 가짜 GPS를 만들지 않는다.
  - 완료 근거: 안내·배너 수정 후 전체 **243개**(phone 184·app 50·protocol 9) 통과, 실패·오류·건너뜀 0, **29초·81개 태스크 실행**, lint 오류 0·경고 12개. 마지막 상수 정리 후 **27초·실행 15/UP-TO-DATE 66**의 증분 실행에서 phone 184개를 재실행했고 app/protocol은 직전 새 결과 유지, 최신 APK 설치 Success. 실제 스와이프의 app_closed 콜백과 서버 manual 종료도 확인해 main이 합의한 debug 범위를 signoff했다. main 제공 manifest는 변경 37개·보호 파일 80개 일치·범위 밖 파일 없음·HEAD `f5c7502`/브랜치 유지·diff check 정상이며 최종 문서 저장 뒤 재생성한다.
  - 검증 범위: 실물 기기·API 26 설치/런타임·release 런타임은 미검증이다. 보호된 차 앱의 0m 겹침 시 세계 범위 확대 현상은 범위 밖으로 남겼다.
  - 종료 경계: 실행 중 서비스의 `onTaskRemoved` → `appClosed()`가 `session.end(app_closed)`를 최선 노력으로 보내고 GPS·소켓 재시도를 중단한다. 현재 서버 통지는 `manual`이다. 오프라인 종료는 큐에 남기지 않으며 force-stop·작업 관리자 Stop은 콜백을 보장하지 않는다. Requesting·위치 권한 없음의 스와이프 즉시 종료도 보장하지 않으며 공유 heartbeat 미수신 2분 초과 만료가 대안이다. 상세는 PHONE_APP.md 5절이다.

## 5. 통합과 제출

- [x] **D1. 데모 리플레이 스크립트** — 11/08 [scripts/demo.sh](../scripts/demo.sh)로 실제 차 `emulator-5554`·폰 `emulator-5556`에서 아래 1~5단계를 끝까지 재현하고 종료 코드 0을 확인했다. `fake-rider`는 사용하지 않았다.
  - 시나리오(기획서 4절 "수요일 밤 10시, 대치동 학원가"):
    1. 차가 학원 근처에 정차(기어 P).
    2. 민지가 폰에서 "데리러 와줘" → 차에서 승인.
    3. 민지가 학원 출입구에서 차로 걸어온다(폰 위치 이동).
    4. 50m 안에 들어오면 실내등이 켜지고 폰에 "실내등을 켰어요"·"실내등 켜진 차를 찾아주세요".
    5. 민지가 뒷문(RR)을 열고 닫으면 탑승 확인 → 양쪽 종료, 실내등 복구.
  - 좌표: 차 정차 위치 `37.49950, 127.06030`, 학원 출입구 `37.49830, 127.06280`(약 260m). 학원 → 차를 15등분해 3초 간격으로 `adb -s emulator-5556 emu geo fix <경도> <위도>`.
  - 서버 `/health`와 기기·설치 앱을 먼저 확인한다. 기본 adb는 `~/Library/Android/sdk/platform-tools/adb`이며 serial·서버 주소는 인자/환경변수로 바꿀 수 있다. Virtual Console 기어 P·RR 문과 앱 요청·수락·화면 확인은 안내 후 총 9번 Enter를 기다린다. 실패·EOF·Ctrl-C 때 후속 단계를 실행하지 않는다.
  - 완료 기준: 스크립트 한 번으로 1~5단계가 재현됨.
  - 실행 결과: 기존 `MinjiP3` 폰 연결을 유지해 요청 → 차 버튼 수락 → 15단계 이동 → 34m Nearby·실내등 ON 안내 → 0m 지도 유지 → RR 열림/닫힘 → 양쪽 `boarded` 종료·홈 복귀를 확인했다. 차 홈은 "민지, MinjiP3"로 민지가 한 번만 표시됐다. 위치 서비스·공유 알림은 없어졌고, 종료 후 차를 Idle에서 재시작해 SDK 실내등 조회값 OFF로 원래 상태 복구를 확인했다. 화면·로그 근거와 검증 범위는 [DEMO_SCRIPT.md 4절](DEMO_SCRIPT.md#4-실행-검증-결과)에 있다.
- [ ] **D2. README 제출본 보완** — 루트 `README.md`에 서버·차 실행, 가짜 탑승자, P1/P2 이력과 P3/P4 실제 폰·차 연동·debug 검증 결과를 반영했다. S5·D3 완료 후 배포 주소·release APK 위치를 채워 제출본을 완성한다.
- [ ] **D3. 릴리스 APK** — 차 앱 `apk/팀명_마중_release.apk`. 팀명은 **사용자에게 확인**한다. 폰 앱도 같은 폴더에 `팀명_마중_phone_release.apk`.
  - 서명: 에이전트가 `keytool`로 `keystore/maejung-release.jks`를 만들고, 비밀번호는 `keystore.properties`에 둔다. **둘 다 `.gitignore`에 넣고 커밋하지 않는다.** 백업은 사용자에게 알린다.
  - release `SERVER_URL`은 S5의 Railway URL이 필요하다(S5 뒤에 한다).
  - 완료 기준: 두 APK가 에뮬레이터에 설치되어 Railway 서버로 D1 시나리오가 동작.
- [ ] **D4. 시연 영상** — 담당: **사용자**(녹화·편집). 에이전트는 D1 순서에 맞춘 촬영 대본(장면, 화면, 내레이션)을 `docs/DEMO_SCRIPT.md`로 만든다.
  - [x] 11/08 [촬영 대본](DEMO_SCRIPT.md) 완료: 기획서의 수요일 밤 10시 대치동 학원가, 실제 차·폰·Virtual Console 조작, 장면별 내레이션·시간, 총 150초 구성.
  - [ ] 사용자 영상 녹화·편집. 대본의 150초는 편집 목표이며 수동 확인을 포함한 D1 실행 소요시간과 다르다.
- [ ] **D5. GitHub 저장소 생성과 푸시** — 담당: **사용자** 확인 후 진행. 푸시 전 `keystore/`, `keystore.properties`, `local.properties`가 추적되지 않는지 확인.

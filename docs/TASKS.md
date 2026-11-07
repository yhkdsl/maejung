# 마중 작업 목록

에이전트는 이 목록을 위에서부터 집어 간다. 각 작업은 **완료 기준을 모두 만족해야** 끝난 것이다. 끝나면 체크하고, 결정·검증 결과를 관련 설계 문서에 반영한다.

- 1차 제출 마감: **2026-11-15(일)**, 목표 11/14. 2차 PT: 12/3~4.
- 설계 문서: [DESIGN.md](DESIGN.md)(원칙·탑승 판정·실내등·SDK), [PROTOCOL.md](PROTOCOL.md)(통신 계약), [SERVER.md](SERVER.md), [CAR_APP.md](CAR_APP.md), [PHONE_APP.md](PHONE_APP.md)
- **담당이 "사용자"인 작업은 에이전트가 하지 않는다.** 계정 생성, 결제, 외부 서비스 설정, 대용량 다운로드 승인은 사용자 몫이다.
- **커밋은 사용자가 요청할 때만 한다.** 형식은 루트 `CLAUDE.md` "처음 온 에이전트가 할 일" 4번.
- 의존 관계: 0절은 언제든 먼저. S1→S2→S3. M1 → A1 → A2 → A3·A4 → A5(S3 필요) → A6·A7·A8. P1 → P2(M1 필요) → P3(S3 필요) → P4. D는 A·P·S가 끝난 뒤.
- **병렬 진행**: M1이 끝나면 차 앱(A)과 폰 앱(P)은 서로 파일이 겹치지 않아 세션을 나눠 동시에 할 수 있다. 공용 파일(`protocol/`, 루트 `settings.gradle.kts`·`build.gradle.kts`)은 한쪽만 고치고, 바꿀 일이 생기면 다른 쪽에 알린다.

## 일정 (11/7 기준, 목표 제출 11/14)

| 날짜 | 에이전트 | 사용자 |
|---|---|---|
| 11/6(금) ✅ | 컨셉 확정, 통신·탑승 신호 확인, 문서 작성 | - |
| 11/7(토) ✅ | 확인 작업(C1·N1·V1), 헤드업 알림 확인, 서버(S1~S4) | - |
| 11/8(일) | M1, A1, A2 | Railway 배포(S5), 팀명, 접수양식 확인(U2) |
| 11/9~10(월~화) | A3, A4, A5, A8 | 폰 에뮬레이터 이미지 다운로드 승인(P1) |
| 11/11~12(수~목) | A6, A7, P2~P4(병렬 세션이면 11/9부터) | 음성 인식 테스트 때 "응"이라고 말하기(A8) |
| 11/13(금) | D1, D2, D3, 기획서 최종본 정리, D4 대본 | 시연 영상 녹화(D4) |
| 11/14(토) | 최종 점검 | GitHub 생성(D5), 기획서·개인정보 동의서 제출 |

## 사용자가 할 일 (모아 보기)

에이전트는 아래 일을 대신하지 않는다. 필요한 시점이 되면 사용자에게 알리고 기다린다.

| ID | 할 일 | 필요한 시점 | 상태 |
|---|---|---|---|
| U1 | 팀명 정하기 (APK 이름, 제출 메일 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`) | D3 전 | 대기 |
| U2 | 공식 접수양식(DOCX) 확인. 기획서를 양식 목차에 맞춰 재구성해야 한다. 사용자가 이전에 다운로드를 거절했으니 에이전트가 임의로 받지 않는다 | 기획서 최종본 전 | 대기 |
| S5 | Railway에 서버 배포, 배포 URL 전달 (README "Railway 배포") | A5 이후 아무 때나, D3 전 필수 | 대기 |
| P1 | 폰 에뮬레이터 이미지(약 1GB) 다운로드 승인 | P2 전 | 대기 |
| A8 | 음성 인식 테스트 때 PC 마이크에 "응"이라고 말하기 | A8 | 대기 |
| D4 | 시연 영상 녹화·편집 | 11/13 | 대기 |
| D5 | GitHub 저장소 생성과 푸시 승인 | 11/14 | 대기 |
| - | 기획서 제출(메일), 개인정보 동의서 서명 | 11/14 | 대기 |

## 0. 확인 작업 (결과에 따라 설계가 바뀐다)

11/07 세 작업 모두 끝났다. 결과는 DESIGN.md 6·7절.

- [x] **C1. 차 에뮬레이터 위치 주입** — 됨. `FixedLocationSource`는 필요 없다(테스트용으로만 둔다).
  - 스파이크에 "10. 위치 받기" 버튼을 추가해 `LocationManager.requestLocationUpdates(GPS_PROVIDER, 3000, 0f)`로 로그를 찍는다. 매니페스트에 `ACCESS_FINE_LOCATION`을 추가하고 `adb shell pm grant com.maejung android.permission.ACCESS_FINE_LOCATION`으로 허용한다.
  - `adb -s emulator-5554 emu geo fix 127.06030 37.49950`(경도, 위도 순서) 후 로그 확인.
  - 완료 기준: 주입한 좌표가 로그에 찍힘. 안 되면 CAR_APP.md 2절에 "데모는 `FixedLocationSource`(D1의 차 좌표)를 쓴다"를 적는다. 결과를 DESIGN.md 7절 표에 추가한다.
- [x] **N1. NaviHelper ETA** — 됨. 경로가 있을 때 `onDrivingInfo`로 남은 거리·시간을 받는다. 앱이 직접 경로를 거는 기능은 사용자 결정 대기.
  - 공식 문서(document.pleos.ai의 NaviHelper API 레퍼런스. 본문이 Dokka shadowRoot 안에 있어 브라우저로 열고 `shadowRoot.textContent`를 읽는다)에서 임의 좌표까지 ETA·거리를 받는 API가 있는지 확인하고, 있으면 스파이크로 호출한다.
  - 완료 기준: 되면 시그니처·의존성·퍼미션을 DESIGN.md 5절에 추가. 안 되면 CAR_APP.md 2절 `EtaCalculator`에 "추정값만 쓴다(`source: estimate`)"를 적는다.
- [x] **V1. 한국어 TTS·STT** — 말하기는 Pleos TTS ON_DEVICE로 됨(안드로이드 기본 TTS는 엔진 없음). 인식기는 있으나 실제 인식은 미확인 → A6에서 버튼 승인을 기본으로, 음성 승인은 되면 추가.
  - Pleos 에뮬레이터에서 `TextToSpeech` 초기화 후 `setLanguage(Locale.KOREAN)` 결과, `SpeechRecognizer.isRecognitionAvailable(context)`를 스파이크 버튼으로 확인.
  - 완료 기준: 결과를 DESIGN.md 7절 표에 추가. STT가 안 되면 CAR_APP.md 3.4의 음성 승인은 생략(버튼만), TTS가 안 되면 화면 문구만이라고 적는다.

## 1. 서버 (`server/`)

- [x] **S1. 프로젝트 생성** — SERVER.md 2~5절대로 독립 Gradle 프로젝트, wrapper 8.12, Dockerfile(Eventoday와 같은 2단계), `application.yml`(포트·비밀키 기본값), `server/.gitignore`(build/, .gradle/).
  - 완료 기준: `cd server && ./gradlew test` 성공, `./gradlew bootRun` 후 `curl localhost:8080/health` → `{"status":"ok"}`.
- [x] **S2. 토큰·REST** — TokenService, PairingCodeStore, `/api/vehicles`, `/api/pairing-codes`, `/api/pairings`, 오류 형식(PROTOCOL.md 2절).
  - 완료 기준: `TokenServiceTest`, `PairingCodeStoreTest` 통과. curl로 등록 → 코드 → 페어링 성공, 토큰 없이 코드 발급 시 `401 INVALID_TOKEN`.
- [x] **S3. WebSocket과 세션** — ConnectionRegistry, MaejungWebSocketHandler, SessionService, SessionSweeper.
  - 완료 기준: SERVER.md 6절의 `SessionServiceTest`, `WebSocketFlowTest`(①~⑤) 통과.
- [x] **S4. 배포 준비** — 루트 README의 서버 절에 Railway 설정 방법(Root Directory `server`, 환경변수 `SPRING_PROFILES_ACTIVE=railway`·`MAEJUNG_TOKEN_SECRET`, 헬스체크 `/health`). 11/07 로컬 `docker build`는 Docker Hub 이미지 메타데이터 조회에서 멈춰 확인하지 못했다(네트워크 문제로 보임). `bootJar`는 jar 하나만 만드는 것을 확인했고 Dockerfile은 Eventoday와 같다. Railway 첫 배포에서 확인한다.
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
- [x] **A3. 차량 신호** (11/07 완료: 기어 P 수신, 승객 문 3개 열림·닫힘 구독, 실내등 지원 여부·현재 값 조회와 켜기·복구를 앱 흐름에서 확인) — `PleosVehicleSignals`(기어, 승객 문 3개, 실내등 캐퍼빌리티·현재 값·구독·set).
  - 완료 기준: Virtual Console로 기어·문을 바꾸면 화면(디버그 표시)에 반영, 실내등 ON/OFF 후 조회값 확인. 실내등 스위치는 구독하지 않고 앱 시작·Sharing 진입 때 조회한다(CAR_APP.md 3.2).
  - Nearby 도중 앱을 강제 종료(`adb shell am force-stop com.maejung`)하고 다시 켜도 실내등이 원래 값으로 돌아오는지(Prefs의 `lightBefore`) A5에서 확인한다.
- [x] **A4. 가짜 서버로 한 바퀴** (11/07 완료: 홈 화면 "가짜 요청 받기" → 수락 → 탑승자가 학원에서 2초 간격 15단계로 접근 → 34m에서 Nearby·실내등 ON → Virtual Console RR 문 열고 닫기 → Ended(boarded)·실내등 OFF 복구 → 3초 뒤 Idle. 로그 `adb logcat -s CarSession`) — `FakeSessionTransport` + D1 좌표로 요청 → 승인 → 접근 → 실내등 → 문 열고 닫기 → boarded.
  - 완료 기준: 서버 없이 에뮬레이터에서 전체 흐름이 보이고, 끝나면 실내등이 원래 값으로 돌아감.
- [x] **A5. 실제 서버 연결** — `WebSocketTransport`, `ApiClient`, 차 등록·토큰 저장, 가족 연결 코드 화면, `session.state` 처리. 11/07 완료: 로컬 서버 + `scripts/fake-rider.mjs`(폰 대신)로 코드 발급 → 페어링(차에 "민지 연결됨") → 요청 → 수락 → 위치 양방향 → 34m Nearby·실내등 ON·폰에 `peer.light on` → RR 문 → 양쪽 `session.ended(boarded)`. Nearby 중 서버를 껐다 켜자 차가 다시 붙어 `session.state none` → Ended(peer_lost)·실내등 OFF 복구. 차 토큰은 Prefs에 남아 재등록 없음.
  - 완료 기준: 로컬 서버에 붙어 코드 발급. 서버를 껐다 켜면 재연결 후 `session.state none`을 받아 진행 중 세션이 Ended(peer_lost)로 정리되고 실내등이 복구됨.
- [x] **A6. 음성** — `PleosVoiceIO`(말하기·듣기 모두 Pleos Speech SDK ON_DEVICE). 11/07: TTS 안내 재생 확인. 안드로이드 SpeechRecognizer는 에뮬레이터에서 준비 콜백도 없이 시간 초과라 Pleos STT로 바꿨고, Pleos STT는 요청 안내 뒤 "말할 준비 됨"까지 확인. **"응"을 실제로 알아듣는지는 사람 발화로 확인해야 한다(사용자 할 일).**
  - 완료 기준: 요청 시 TTS 안내가 나옴. STT가 되면 "응"으로 승인됨.
- [x] **A8. 알림·길안내·백그라운드** — `PickupNotifications`(스파이크 `spike/PickupAlert.kt` 참고), `PickupAlertReceiver`, `PickupNavigator`, `CarSessionService`(CAR_APP.md 2절 규칙). 11/07 완료(음성 승인 제외): 내비가 앞에 있는 상태에서 헤드업 요청 알림 → 수락(헤드업·알림 센터 둘 다) → 내비를 띄운 채로 위치 공유·34m 근접 안내·실내등·탑승 감지까지 동작. 차를 1,099m 떨어뜨리면 수락 직후 길안내 시작, 차에서 "공유 끝내기"하면 길안내 취소. **음성 승인은 미확인**: A6에서 Pleos STT로 바꿔 인식 준비까지는 확인, 실제 발화 확인이 남았다.
  - 완료 기준: 내비 화면이 앞에 있을 때 요청이 오면 헤드업 알림이 뜨고 수락·거절이 동작. 차와 탑승자가 500m 이상이면 수락 후 길안내가 시작되고, 탑승 감지·수동 종료 시 길안내가 취소됨. 마중 앱이 화면 뒤에 있어도 위치 전송과 탑승 감지가 계속됨.
  - 음성 승인 테스트는 실제 발화가 필요하다. 에뮬레이터에서 PC 마이크를 켜고 사용자에게 "응"이라고 말해 달라고 요청한다.
- [x] **A7. 위치·ETA** — `AndroidLocationSource`(C1 결과 반영, 안 되면 `FixedLocationSource`), heartbeat 전송(PROTOCOL.md 3.2), `EtaCalculator`(N1 결과 반영), `eta.update` 전송 규칙. 11/07 완료: 공유 화면 "189m 남동쪽, 도착까지 약 1분(예상)", 접근할수록 폰에 `peer.eta` 60→40→28→16초, 정차 3분 넘게 유지해도 끊기지 않고 30초마다 ETA 재전송. `EtaCalculatorTest` 5개. 방향은 정차 중 차 방향을 몰라 화살표 대신 8방위 글자로 보여준다.
  - 완료 기준: 정차 상태로 3분 두어도 서버가 `peer_lost`로 끊지 않음. 탑승자 위치를 옮기면 SharingScreen의 거리·방향·ETA가 바뀜. 서버 로그에 `eta.update`가 30초 간격으로 찍힘.

## 4. 폰 앱 (`phone/`)

- [ ] **P1. 폰 에뮬레이터** — Google APIs(Play 서비스) 시스템 이미지로 폰 AVD 생성. 이미지 다운로드(약 1GB)는 **사용자에게 먼저 묻는다.**
  - 완료 기준: 차 에뮬레이터와 동시에 실행되고 `adb devices`에 둘 다 보임. AVD 이름과 실행 방법을 루트 CLAUDE.md "에뮬레이터 실행"에 추가.
- [ ] **P2. 모듈과 화면** — PHONE_APP.md 2~3절, 화면 3개(서버 없이 상태를 바꿔 볼 수 있는 디버그 메뉴 포함).
  - 완료 기준: `./gradlew :phone:assembleDebug` 성공, 폰 에뮬레이터에서 세 화면과 종료 문구 6가지가 보임.
- [ ] **P3. 서버 연동** — 페어링, 요청, PHONE_APP.md 4절 전이 전부.
  - 완료 기준: 로컬 서버 + 차 에뮬레이터와 함께 페어링 → 요청 → 차에서 승인 → 양쪽 위치 표시 → 차에서 거절했을 때 "엄마가 지금은 못 온대요"까지 확인.
- [ ] **P4. 위치 포그라운드 서비스** — Sharing 동안만 전송, 알림 "끝내기", 앱 종료 시 `session.end(app_closed)`.
  - 완료 기준: 폰 화면을 꺼도 차 쪽에 위치가 계속 들어옴. 최근 앱에서 스와이프하면 차가 Ended(manual)가 됨.

## 5. 통합과 제출

- [ ] **D1. 데모 리플레이 스크립트** — `scripts/demo.sh`.
  - 시나리오(기획서 4절 "수요일 밤 10시, 대치동 학원가"):
    1. 차가 학원 근처에 정차(기어 P).
    2. 민지가 폰에서 "데리러 와줘" → 차에서 승인.
    3. 민지가 학원 출입구에서 차로 걸어온다(폰 위치 이동).
    4. 50m 안에 들어오면 실내등이 켜지고 폰에 "실내등 켜진 차가 엄마 차예요".
    5. 민지가 뒷문(RR)을 열고 닫으면 탑승 확인 → 양쪽 종료, 실내등 복구.
  - 좌표: 차 정차 위치 `37.49950, 127.06030`, 학원 출입구 `37.49830, 127.06280`(약 260m). 학원 → 차를 15등분해 3초 간격으로 `adb -s emulator-5556 emu geo fix <경도> <위도>`.
  - 스크립트가 할 수 없는 단계(Virtual Console에서 기어 P, RR 문 열고 닫기)는 화면에 안내 문구를 띄우고 Enter를 기다린다.
  - 완료 기준: 스크립트 한 번으로 1~5단계가 재현됨.
- [ ] **D2. README** — 루트 `README.md`. 공모전 필수 항목: ① 앱 이름과 한 줄 소개 ② 실행 방법(서버·차·폰) ③ 테스트 환경과 필요한 설정(Pleos Connect Emulator v2.1.0, Virtual Console, 폰 AVD, 서버 주소) ④ APK 위치(`apk/` 폴더). 그 밖에 구성도와 설계 문서 링크.
- [ ] **D3. 릴리스 APK** — 차 앱 `apk/팀명_마중_release.apk`. 팀명은 **사용자에게 확인**한다. 폰 앱도 같은 폴더에 `팀명_마중_phone_release.apk`.
  - 서명: 에이전트가 `keytool`로 `keystore/maejung-release.jks`를 만들고, 비밀번호는 `keystore.properties`에 둔다. **둘 다 `.gitignore`에 넣고 커밋하지 않는다.** 백업은 사용자에게 알린다.
  - release `SERVER_URL`은 S5의 Railway URL이 필요하다(S5 뒤에 한다).
  - 완료 기준: 두 APK가 에뮬레이터에 설치되어 Railway 서버로 D1 시나리오가 동작.
- [ ] **D4. 시연 영상** — 담당: **사용자**(녹화·편집). 에이전트는 D1 순서에 맞춘 촬영 대본(장면, 화면, 내레이션)을 `docs/DEMO_SCRIPT.md`로 만든다.
- [ ] **D5. GitHub 저장소 생성과 푸시** — 담당: **사용자** 확인 후 진행. 푸시 전 `keystore/`, `keystore.properties`, `local.properties`가 추적되지 않는지 확인.

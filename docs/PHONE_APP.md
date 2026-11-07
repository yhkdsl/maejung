# 마중 폰 앱 (탑승자용)

탑승자(학원생 등)가 쓰는 일반 Android 앱이다. **2026-11-07, P3 완료 커밋 `f5c7502` 기반의 P4 구현과 합의한 debug 범위의 main QA를 완료했다.** 앱의 기본 진입은 실제 서버 연결이며, P2 오프라인 미리보기는 debug에서 명시적으로 선택한다. 2절은 모듈·실행 설정, 3절은 분리된 P2 미리보기, 4절은 P3/P4 통신 구조, 5절은 P4 위치·서비스·실제 지도 구조다. P1/P2 이력은 6절, P3 검증 결과는 7절에 보존한다. **P4 전체 243개 GREEN·최종 증분 실행·최신 APK 안내/마커·양쪽 공유·이동/근접/탑승·알림 종료·app_closed 서버 종료 근거는 8절에 기록했다.** 독립 검토 결과는 최종 QA 근거와 함께 관리한다. 실물 기기·release APK 런타임·API 26 설치/런타임은 미검증이다.

메시지 계약의 기준은 [PROTOCOL.md](PROTOCOL.md)다. P3 REST·WebSocket·자격 보관을 유지하고 P4도 기존 메시지만 사용한다. GPS·위치 권한·위치 포그라운드 서비스·실제 지도는 P4 범위다. 이번 작업은 `app/`·`server/`·`protocol/` 소스를 변경하지 않는다. 빌드 공통 규칙은 [CAR_APP.md](CAR_APP.md) 1절을 참고한다.

## 0. 실제 앱·개발용 대역·기존 검증의 구분

- 폰 앱은 P3부터 실제 REST·WebSocket을 사용한다. `scripts/fake-rider.mjs`는 같은 흐름을 참고하거나 차·서버만 시연할 때 쓰는 별도 개발용 대역이며, 이를 실행한 결과는 폰 앱 검증으로 계산하지 않는다. Node 22 이상 내장 WebSocket을 사용한다.
- 차 화면에서 발급받은 코드를 넣어 실행한다:

  ```bash
  node scripts/fake-rider.mjs <6자리코드> [서버주소=http://localhost:8080] [이름=민지]
  ```

- `POST /api/pairings` → 받은 riderToken으로 WebSocket 연결 → 첫 `session.state` 수신 시 `pickup.request` → `pickup.accepted` 후 2초 간격 위치 전송 순서다. 학원에서 차까지 15단계로 접근한 뒤 도착 좌표를 계속 보낸다. 실제 휴대폰 GPS를 읽지 않는다.
- 대역 스크립트는 토큰·이름 영구 보관, 재연결, Android UI·권한 요청·포그라운드 서비스를 구현하지 않는다. `session.ended`나 `error`를 받으면 전송을 멈추고 소켓을 닫는다. 연결이 닫히면 프로세스가 종료되므로 새 실행에는 새 연결 코드가 필요하다. P3 폰 앱의 보관·재연결 동작과 구분한다.
- 11/07 기존 에이전트 기록에는 이 대역과 로컬 서버·차 에뮬레이터의 페어링부터 탑승 종료까지 검증이 있다. 이는 폰 앱의 실제 통신 검증이 아니다. 아래 P2 실행 근거는 메인 QA가 제공했고 문서 작성 담당은 별도로 에뮬레이터·빌드를 실행하지 않았다.
- **이전 검증 이력**: 앞선 세션의 JBR 21·offline·`--rerun-tasks` 결과는 app 46 + protocol 9 + server 34 = 89개, 실패 0이다. 이 이전 결과에는 phone 테스트가 포함되지 않는다.
- 기존 P2 메인 검증 이력(UI 재설계 전): phone 테스트 **16개 통과·debug 조립 성공(7초)**, JBR 21로 `:protocol:test :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --offline --no-daemon --console=plain` 성공(12초·47개 태스크 실행). app 46 + protocol 9 = 55개, 실패·오류·건너뜀 0이다. 서버는 소스 변경 없이 재실행하지 않았고 34개는 이전 결과다. 폰 화면·복원 근거와 UI 재설계의 새 검증 상태는 6절에 정리한다.
- UI 재설계 최종 검증(메인 제공): `:phone:testDebugUnitTest :phone:assembleDebug :phone:lintDebug` 오프라인 통합 실행 **성공(19초·55개 태스크: 실행 14·최신 상태 41)**. 테스트 24개를 모두 재실행해 통과했고 lint는 오류 0·기존 경고 8개, 최종 debug APK 설치는 Success다. API 27 테마를 기본 리소스와 `values-v27`로 분리했고 불필요한 null 검사도 제거했다. 독립 소스·시각 검토와 네이티브 AVD QA는 PASS이며 상세는 6절이다. API 26 설치·런타임은 미검증이다.
- Docker는 설정 작성 후 Hub timeout으로 image build 성공 미확인(S4 검증 대기), Railway는 배포 미진행이다. 차 앱의 음성 코드(A6)는 구현돼 있으나 실제 발화 STT는 사용자 검증 대기다.

## 1. 1차 범위

- **1차(11/15)는 Android만.** iOS는 만들지 않는다.
- 화면 3개: 연결, 홈(요청), 마중 중. 그 밖에 요청 대기·종료 문구는 홈 화면 안의 상태 표시로 처리한다.
- 승인된 UI를 네이티브 Compose로 적용했다. P2 debug 미리보기는 `PreviewSharingMap`의 로컬 예시 지도·샘플 HUD를 유지한다. P4 실제 공유 화면은 `SharingMap`의 osmdroid 지도와 실제 GPS·수신 차량 위치를 사용하고, 지도 아래 거리·이동 기준 방향·수신 ETA와 실내등 안내를 표시한다. 합의한 debug 범위의 main 네이티브 QA를 완료했다.
- 차 이름은 1차에서 "엄마 차"로 고정한다(연결할 때 입력받는 건 PT 단계).
- P2는 디버그 오프라인 UI, P3는 실제 페어링·요청·수락·거절·종료·재연결·토큰 보관, P4는 GPS·위치 공유 서비스·실제 지도로 나눈다. P2 화면의 거리·ETA·실내등은 샘플이다.

## 2. 모듈과 빌드·실행 설정

- 루트 `settings.gradle.kts`가 `:app`·`:protocol`·`:phone`을 포함한다. `phone/`의 namespace·applicationId는 `com.maejung.phone`, minSdk 26, **compileSdk 35**, targetSdk 34, JVM 17이다.
- 일반 휴대폰 앱이다. `android.hardware.type.automotive`를 선언하지 않는다.
- 기존 AGP 8.10.1·Kotlin 2.1.21·Compose BOM `2025.05.01`·Material3와 `project(":protocol")`을 재사용한다. Activity Compose `1.10.1`, Lifecycle Compose `2.9.0`, Core KTX `1.16.0`, 차 앱과 같은 Coroutines Android `1.10.2`를 고정한다. 통신은 OkHttp `4.12.0`, 테스트는 MockWebServer `4.12.0`·Coroutines Test `1.10.2`, P4 지도는 osmdroid `6.1.20`이다. 위치는 Android LocationManager이며 GMS 위치·Pleos 의존성은 추가하지 않는다.
- `BuildConfig.SERVER_URL`은 debug `http://10.0.2.2:8080`, release는 `-Pmaejung.serverUrl` 우선이며 없으면 `local.properties`의 같은 속성이다. 폰 release는 HTTPS 주소가 필요하다. 실제 통신 경로는 debug·release가 같으며 release에는 미리보기 진입이 없다. release APK 생성·런타임 검증은 아직 하지 않았다.
- Manifest의 Application은 `MaejungPhoneApp`, 런처는 `com.maejung.phone/.MainActivity`, `windowSoftInputMode="adjustResize"`다. P4는 `INTERNET` 외에 `ACCESS_FINE_LOCATION`·`ACCESS_COARSE_LOCATION`·`FOREGROUND_SERVICE`·`FOREGROUND_SERVICE_LOCATION`·`POST_NOTIFICATIONS`와 `foregroundServiceType="location"`인 `LocationShareService`를 사용한다. 로컬 HTTP 예외는 debug의 `10.0.2.2`·`localhost`·`127.0.0.1`에 한정하고 release에서는 허용하지 않는다.
- debug APK: `phone/build/outputs/apk/debug/phone-debug.apk`. P2 조립·설치 이력은 6절, P3 결과는 7절, P4 최종 검증 결과는 8절이다. 현재 실행 절차는 [README](../README.md)의 폰 앱 실제 연결과 위치 공유를 참고한다.

## 3. P2 화면과 로컬 상태 미리보기 (구현 완료·debug 전용)

아래는 보존한 P2 미리보기 구성이다. `PhoneState`의 `PreviewScene`과 `PhoneViewModel`은 로컬 화면 상태만 다루며 서버 세션과 별개다. 현재 `MainActivity`의 기본 루트는 `LivePhoneApp`이고, 명시적으로 미리보기에 진입했을 때만 `PhoneApp`을 표시한다. `BuildConfig.DEBUG` 검사는 양쪽 루트에서 유지한다.

```
phone/src/main/java/com/maejung/phone/
├── MainActivity.kt               실제 상태를 구독하는 런처, 미리보기 ViewModel 별도 유지
├── session/
│   └── PhoneState.kt             입력·검증 오류·PreviewScene·SharingInfo
└── ui/
    ├── PhoneApp.kt               마중 브랜드·상단 배지/메뉴·화면 선택·종료 확인
    ├── PairScreen.kt             이름·코드 단일 편집기/여섯 칸 표시·형식 검증
    ├── HomeScreen.kt             차량 일러스트·요청·요청 대기·종료 안내
    ├── SharingScreen.kt          지도·거리/나침반/ETA HUD·실내등 안내·공유 종료
    ├── PreviewSharingMap.kt      debug 전용 북쪽 고정 로컬 도식·정차한 차/아이 마커
    ├── SharingCompass.kt         이동 기준 상대각·초/분 ETA 표시 계산
    ├── PhoneComponents.kt        네이비/펄/시안 테마·픽셀 마크·스크롤·버튼
    ├── PhoneIllustrations.kt     Compose Canvas 연결·은색 차량 일러스트
    └── PhoneViewModel.kt         로컬 입력·미리보기 전이, SavedStateHandle 복원
```

### 입력과 화면

- 공통 상단은 픽셀 마크와 "마중" 브랜드, 작은 "오프라인" 배지, 우측 미리보기 메뉴다. 딥 네이비 `#002C5F`·펄 `#F7F8FA`·시안 `#00AAD2` 테마를 사용한다.
- 연결 화면의 제목은 "엄마 차와 연결해요"(두 줄), 입력은 "내 이름"·"차량 연결 코드", 버튼은 "입력 확인 후 홈 미리보기"다. 이름은 서버와 같이 `trim()` 후 Kotlin `String.length`가 1~20이어야 한다(UTF-16 코드 단위, 내부 공백 유지). 빈칸·공백뿐인 이름·20 초과 이름은 오류 안내와 함께 연결 화면에 남는다. 잘못된 입력을 조용히 자르거나 바꾸지 않는다.
- 코드는 ASCII `0`~`9` 정확히 6자리다. 포커스 중에는 텍스트·커서가 보이는 단일 편집기를 쓰고, 포커스가 없고 6자리 이하 ASCII 숫자이면 여섯 칸으로 표시한다. 문자·전각 숫자·공백·7자리 이상 등 잘못된 입력도 원문이 보이는 단일 편집기로 유지하며 자르거나 숫자만 추리지 않는다. 불완전한 코드·전각/다른 문자 숫자·문자·앞뒤 공백은 검증에서 허용하지 않는다. 검증은 로컬 형식 검사이며 실제 코드 존재·만료·사용 여부는 확인하지 않는다.
- 홈은 이름 인사와 "이제 집에 갈까요?", 네이티브 은색 차량 일러스트·"엄마 차" 정보, "데리러 와줘 · 미리보기" 버튼을 표시한다. 누르면 요청 대기 예시가 되고 실제 요청은 보내지 않는다. 다음 공유 화면은 상단 메뉴에서 직접 선택한다. 요청 대기에서 자동 승인·공유 전환은 없다.
- 마중 중은 북쪽 고정 로컬 예시 지도에 정차한 엄마 차와 아이의 내 위치를 표시하고, 지도 하단 HUD에 거리·"이동 기준" 나침반·도착예상을 모은다. 일반 공유 샘플은 120m·90초(화면 "약 2분")·실내등 꺼짐, 근접은 20m·30초(화면 "약 30초")·실내등 켜짐이다. 근접 지도에서도 차의 지도 좌표는 고정하고 아이를 차 가까이 옮겨 확대한다. 실내등 켜짐에는 지도 아래 "실내등 켜진 차를 찾아주세요"를 표시한다. "위치 공유 끝내기"는 미리보기 종료 확인창을 연다.
- 두 공유 샘플은 북쪽 기준 차 방위 80°·이동 방향(course) 35°이므로 나침반 화살표는 이동 기준 오른쪽 앞 45°다. 나침반의 12시는 이동 방향이며 지도 북쪽·휴대폰 방향과 별개다. course가 없으면 나침반은 `—`로 표시하고 화살표와 지도 위 이동 방향 표시를 숨긴다. 실제 GPS course 수집·유효성 처리는 5절의 P4 구조로 분리한다.
- 공유·근접 지도는 최소 높이를 유지하면서 화면에 남는 세로 공간을 채운다. 지도·실내등 안내와 종료 버튼 사이에는 정해진 여백만 두며, 작은 화면·큰 글꼴·가로 화면은 내용 전체를 스크롤한다.
- 정보 없음은 "위치 정보를 기다리고 있어요"와 "위치 정보가 없어 차 위치를 표시할 수 없어요."를 표시한다. 지도 바탕만 남기고 차·아이 마커와 이동 방향·나침반 화살표를 모두 숨기며 거리·나침반·ETA는 `—`, 실내등은 "실내등 정보를 확인할 수 없어요."로 표시한다. 이전 근접·공유 샘플 값은 남기지 않는다.
- 미리보기 화면·이름·코드·입력 오류 표시 여부는 `SavedStateHandle`에 보존하고 `StateFlow`로 화면에 전달한다. `PhoneComponents`의 페이지는 스크롤 가능하며 `PhoneApp`은 시스템 영역·키보드 여백을 적용한다. 회전·키보드·프로세스 재생성 QA는 6절의 P2 이력이다. 저장된 장면이 있어도 앱 시작 시 실제 연결이 기본이며, 해당 장면은 미리보기를 직접 열었을 때만 보인다. 실제 토큰은 미리보기 상태·SavedStateHandle에 넣지 않는다.
- 요청 대기·공유의 종료 버튼 또는 시스템 뒤로 가기는 "미리보기를 끝낼까요?" 확인창을 연다. "미리보기 종료"를 누르면 manual 종료 예시, "계속 보기"는 현재 화면 유지다. 종료 상태는 "홈 미리보기로"를 눌러 돌아오며 자동 3초 복귀는 없다. 홈의 뒤로 가기는 입력을 보존한 채 연결 화면으로 돌아간다.
- 미리보기 메뉴·연결·요청 전환은 `BuildConfig.DEBUG`에서만 허용한다. 화면과 행동에는 로컬 미리보기임을 표시하며 실제 차에 알림·요청을 보내지 않는다. 실제 연결 화면에서 별도 메뉴로 진입하고 "실제 연결 화면으로"로 빠져나온다. 실제 페어링 중이거나 Requesting·Sharing이면 미리보기에 진입할 수 없고, 미리보기 중 실제 활성 세션이 복원되면 실제 화면으로 돌아간다. release는 미리보기를 제공하지 않으며 실제 연결·요청을 사용한다.

### 디버그 메뉴

상단의 작은 "오프라인" 배지에는 "오프라인 미리보기 · 실제 연결·요청·위치공유 없음" 접근성 설명이 있다. 우측 세 점 버튼("오프라인 미리보기 메뉴")으로 장면 선택창을 연다. 기존 하단 메뉴 버튼을 대체하며, 메뉴의 12개 항목은 `PreviewScene.label`과 같다.

| 메뉴 표시 | 로컬 상태 |
|---|---|
| 초기 · 연결 입력 | `INITIAL` |
| 홈 · 요청 전 | `HOME` |
| 요청 대기 | `REQUESTING` |
| 공유 · 예시 120m | `SHARING` |
| 공유 · 정보 없음 | `SHARING_NO_DATA` |
| 근접 · 예시 20m | `NEARBY` |
| 종료 · 탑승 확인 | `ENDED_BOARDED` |
| 종료 · 거절 | `ENDED_REJECTED` |
| 종료 · 직접 종료 | `ENDED_MANUAL` |
| 종료 · 요청 시간 초과 | `ENDED_REQUEST_TIMEOUT` |
| 종료 · 연결 끊김 | `ENDED_PEER_LOST` |
| 종료 · 1시간 경과 | `ENDED_MAX_DURATION` |

위 장면 메뉴는 미리보기 안에서만 표시한다. release에는 이 메뉴와 미리보기 진입이 없다. P2의 입력·상태·복원·release 미리보기 차단 테스트는 `phone/src/test/java/com/maejung/phone/ui/PhoneViewModelTest.kt`의 16개이며, 이전 통과 이력은 6절에 남긴다. 실제/미리보기 행동 경계는 P3의 `LivePhoneControllerTest.kt`가 다룬다. 현재 P3 테스트·release APK 런타임 결과는 이전 P2 결과와 별도로 확인한다.

### 종료 문구 6가지

P2는 아래 문구를 홈의 종료 상태로 미리보기한다. P3 실제 화면도 수신한 `session.ended`의 이유에 같은 문구를 사용한다. P2 표시는 서버 종료 검증이 아니다.

| reason | 문구 |
|---|---|
| `boarded` | "탑승 확인! 위치공유를 끝냈어요" |
| `rejected` | "엄마가 지금은 못 온대요" |
| `manual` | "위치공유를 끝냈어요" |
| `request_timeout` | "응답이 없어요. 다시 요청해 볼까요?" |
| `peer_lost` | "연결이 끊겨서 위치공유를 끝냈어요" |
| `max_duration` | "1시간이 지나 위치공유를 끝냈어요" |

## 4. P3/P4 통신 구조와 상태 전이

통신 계약의 기준은 [PROTOCOL.md](PROTOCOL.md)다. 메시지 형식 변경·추가가 필요하면 문서를 먼저 수정하고 `protocol/` → `server/` → `app/` → `phone/` 순서로 맞춘다. 폰 작업에서 차 앱·서버 코드 변경이 필요하면 사용자에게 변경 필요사항과 영향을 먼저 알리고 해당 코드는 수정하지 않는다.

**P3/P4 경계**: 아래 통신·화면 전이는 P3에서 검증한 기반이며, P4는 위치 권한·GPS·`LocationShareService`·알림·실제 지도를 붙인다. 위치 없이 요청·수락·종료하는 흐름을 유지한다. 초기 `session.state` 동기화가 끝난 연결에서만 새 요청을 허용하며, 4409는 자격을 보존한 채 자동 재연결을 멈추고 명시적인 다시 연결을 기다린다. P3 검증 이력은 P4 서비스·지도 검증을 대신하지 않는다.

실제 통신·위치·UI 소유권은 다음과 같이 나눈다. P4 자동 검증과 main 네이티브 QA 근거는 8절에 기록한다.

| 담당 | 역할 |
|---|---|
| `MaejungPhoneApp`·`PhoneSessionManager` | Application 범위에서 WebSocket·세션 StateFlow·내 위치와 3초 heartbeat 소유. Activity는 구독하고 가시성·권한에 따른 서비스 실행 조건을 조정 |
| P3 `net/ApiClient`·`WebSocketTransport` | REST 페어링·토큰 인증·메시지 전송·재연결(PROTOCOL.md 3절) |
| P3 `store/Prefs` | riderId·vehicleId·riderToken·riderName을 하나의 SharedPreferences 값으로 보관·인증 실패 시 삭제 |
| `LivePhoneState` | Unpaired·Idle·Requesting·Sharing·Ended, 연결·초기 동기화·실제 수신 정보, P4 `ownLocation`·`locationNotice` |
| P3 `LivePhoneApp`·`LivePhoneController` | 실제 화면과 명시적 debug 미리보기의 경계. 입력 초안만 화면 복원 대상이며 토큰·연결 수명은 소유하지 않음 |
| P4 `location/AndroidLocationSource` | 차와 같은 `LocationManager.GPS_PROVIDER`. 신선한 실제 fix와 GPS course 제공, GMS 사용 없음 |
| P4 `LocationShareService`·서비스 정책/컨트롤러 | Sharing·위치 권한 조건에서만 GPS 구독과 위치 FGS·알림 유지. fix는 manager에 전달하며 종료 시 구독 해제 |
| P4 `LiveSharingProjection`·`SharingMap` | 실제 위치의 직선거리·방위·course 상대각과 수신 ETA를 표시, osmdroid 실제 지도. 로컬 도식은 debug `PreviewSharingMap`으로 분리 |

실제 연결을 Activity 범위 ViewModel만으로 소유하지 않는다. Application 시작 때 보관한 자격이 있으면 이를 복원하고 소켓에 연결한다. `PhoneSessionManager`가 들어오는 메시지를 구독한 뒤 연결하므로 첫 `session.state`를 놓치지 않게 한다. P2의 `PreviewScene`은 아래 실제 세션 상태 머신을 대체하지 않는다.

아래는 P3 전이에 P4 위치 수명을 연결한 기준이다. 현재 서버의 `session.state`는 상태와 탑승자 정보만 복원하며, 이전 위치·ETA·실내등 값이나 종료 이유는 재전송하지 않는다.

| 현재 | 이벤트 | 다음 | 동작 |
|---|---|---|---|
| Unpaired | 연결 성공(`POST /api/pairings` 201) | Idle | 토큰·이름 저장, WebSocket 연결 |
| Unpaired | 404·400 | Unpaired | "코드를 다시 확인해 주세요" |
| Idle | "데리러 와줘" 버튼 | Requesting(null) | fine·coarse 권한 요청 후 보관한 이름으로 `pickup.request {riderName}`. 거부해도 요청·위치 불가 안내. **연결 + 초기 동기화 완료일 때만 허용**, 아직 GPS·위치 FGS 없음 |
| Requesting(null) | 최초 요청의 10초 기한까지 `pickup.pending`·`error` 또는 유효한 상태 스냅샷이 없음 | Idle("연결을 확인해 주세요") | 일반 끊김·재연결에도 최초 기한 유지. 만료 시 동기화 해제, 새 요청 차단 후 서버 상태 재확인. 4401·4409 뒤에는 자동 재접속하지 않음 |
| Requesting | `pickup.pending` | Requesting(sessionId) | "엄마 차에 알렸어요" |
| Requesting | `error VEHICLE_OFFLINE` | Idle("차가 꺼져 있어요") | |
| Requesting | `error SESSION_ALREADY_ACTIVE` | Idle("이미 다른 요청이 진행 중이에요") | |
| Requesting | 취소 버튼, 연결·동기화 완료 및 전송 큐 수락 | Ended(manual) | `session.end manual`. GPS·서비스 처리는 없음 |
| Requesting | `pickup.accepted` | Sharing | 해당 sessionId의 실제 공유 화면. 위치 권한·앱 가시성에 따라 서비스 시작 또는 화면 복귀 후 재시도(5절) |
| Sharing | `peer.location`, `peer.eta`, `peer.light` | Sharing | 현재 sessionId의 유효한 수신 정보만 갱신. 실내등은 true일 때만 강조 |
| Sharing | 화면/알림 종료, 연결·동기화 완료 및 전송 큐 수락 | Ended(manual) | `session.end manual`, GPS·서비스·heartbeat·위치 정보 정리 |
| Requesting·Sharing | 연결 끊김·미동기화·전송 실패 중 종료 시도 | 현재 활성 상태 유지 | 연결 상태를 안내하고 다시 연결 후 종료 재시도. 종료 메시지를 보냈다고 표시하거나 자동 예약하지 않음 |
| Requesting·Sharing | 현재 sessionId의 `session.ended(reason)` | Ended(reason) | 수신/내 위치 정리, GPS·서비스·heartbeat 중지 |
| Requesting·Sharing | `session.state none` | Ended(peer_lost) | 유실된 서버 세션과 위치·서비스 정리 |
| 자격이 있는 상태 | 유효 sessionId의 `session.state requested` | Requesting(sessionId) | 최초 연결·재연결 때 요청 복원, 다른 세션이면 이전 수신 정보 정리 |
| 자격이 있는 상태 | 유효 sessionId의 `session.state sharing` | Sharing | 공유 복원. 새 세션이면 이전 내 위치·수신 정보·heartbeat 정리. 같은 세션은 스냅샷 동기화 후 heartbeat 재개. 서비스 조건 재확인 |
| Idle | `session.state none` | Idle | 초기 동기화 완료, 새 요청 가능 |
| Ended | `session.state none` | Ended | 동기화 완료, 기존 종료 안내·3초 복귀 유지 |
| Ended | 3초 뒤 | Idle | |
| 자격이 있는 상태 | 일반 연결 끊김 | 현재 세션 상태 유지 | 동기화 해제, 자동 재연결. 연결 후 `session.state`로 재판정 |
| 아무 상태 | WebSocket close 4401 | Unpaired | 보관한 페어링 자격 삭제, 자동 재연결·GPS·서비스·heartbeat 중단 |
| 자격이 있는 상태 | WebSocket close 4409 | 현재 상태 + REPLACED | 자격 보존, 동기화 해제, 자동 재연결·새 요청·GPS·서비스·heartbeat 중단. "다시 연결"로 명시적 재접속 |

연결은 OkHttp ping 15초, 일반 실패 때 1·2·4·8·최대 15초 간격으로 재시도한다. 명시적 종료·4401·4409 뒤에는 자동 재연결하지 않는다. 소켓을 교체한 뒤 이전 소켓 콜백이 새 연결 상태를 덮어쓰지 않게 구분한다.

아직 sessionId를 받지 못한 요청의 10초 응답 기한은 일반 끊김이나 재접속 시 취소·연장하지 않는다. 기한이 지나면 Idle과 "연결을 확인해 주세요"로 돌아가고 `synchronized=false`로 새 요청을 막는다. 일반 연결에서는 재접속해 새 `session.state`를 받고, `requested`·`sharing`이면 기존 서버 세션을 복원하고 `none`이면 동기화된 Idle로 만든다. 이때 새 픽업 요청을 자동 전송하지 않는다. 4401·4409 뒤에는 이 확인을 위한 자동 재접속도 하지 않는다.

초기 동기화 전에는 소켓이 열려도 요청을 보낼 수 없다. 다른 sessionId의 수신 메시지와 해석할 수 없는 메시지는 상태를 바꾸지 않으며, 활성 스냅샷에는 sessionId가 필요하다. 새 세션과 종료 전이에서 이전 ETA·내 위치·상대 위치·실내등을 정리한다. P4 실제 공유 화면은 좌표 숫자·지도 준비 문구를 실제 지도와 지도 아래 HUD로 바꾼다. 거리는 내 GPS와 차 사이 직선거리, 방향은 GPS 이동 기준이며 ETA만 `peer.eta`에서 받는다. 값이 없으면 `—`로 표시한다. 화면 종료 버튼은 "마중 끝내기"다.

종료 문구는 3절의 6가지를 사용하며, P3는 Ended에서 3초 뒤 자동으로 Idle로 돌아간다. 전송 성공은 WebSocket 송신 큐 수락을 뜻하며 서버 확인 응답을 뜻하지 않는다. 다음 연결의 `session.state`가 최종 세션 상태를 동기화한다. P2 종료 미리보기의 수동 홈 복귀와 구분한다.

## 5. P4 위치 전송과 앱 종료

사용자 확정 요구를 구현했다. 초기 계획의 Fused 위치원·이동량 기반 전송 규칙보다 아래 기준을 우선한다. **P4 debug 범위 main QA는 완료했으며 실행별 근거와 검증 경계는 8절에 기록한다.**

- **위치원·course**: `location/AndroidLocationSource`는 차와 같은 Android `LocationManager.GPS_PROVIDER`를 사용한다(GMS 없음). 마지막 위치도 신선도·좌표·정확도 유효성을 확인한 실제 fix만 받아 이후 GPS 업데이트를 구독한다. GPS bearing은 유효한 속도가 임계 조건을 만족할 때만 사용하고, 그렇지 않으면 유효 GPS fix 사이 변위로 course를 구한다. 정지 중 보고된 bearing 0을 이동 방향으로 간주하지 않으며 기기 방향 센서도 쓰지 않는다. 유효한 이동 방향이 없으면 화살표와 course 표시를 숨기고 `—`로 둔다. 속도 임계 조건·GPS 변위 fallback 수정 후 통합 GREEN과 최신 APK의 정지 `—`를 확인했고 실제 GPS 이동·근접·탑승 종료 근거는 8절에 기록한다.
- **권한과 시작 시점**: "데리러 와줘" 클릭에서 fine·coarse를 함께 요청하며 coarse만 허용해도 Sharing·위치 FGS 실행 자격은 유효하다. 거부해도 픽업 요청은 진행하고 위치 없이 요청됐음을 분명히 안내한다. Requesting이나 위치 권한이 없는 상태에서는 GPS·위치 FGS를 시작하지 않는다. 권한 회수·GPS 사용 불가도 안내하며 수집을 중지한다. 이 API 36 에뮬레이터의 coarse-only는 초기 LOW_POWER·10분 간격/GNSS 꺼짐 상태에서 fix가 지연됐지만 이후 실제 난독화 GPS를 받았고 화면에 1286m가 표시됐다. 영구적인 GPS 불가를 뜻하지 않으며 적시·정확한 이동에는 fine이 필요하다. 실제 fix가 없으면 좌표를 만들거나 보내지 않는다. 대략적 위치의 정밀도·정확한 위치 필요 안내는 첫 fix 전부터 제공하며 coarse 허용 기준은 유지한다. 최신 거부 상태 캡처에서 안내를 확인했다(8절).
- **Sharing 전용 heartbeat**: 서비스가 받은 fix는 Application 소유 `PhoneSessionManager.updateOwnLocation()`으로 전달한다. 유효 sessionId의 Sharing·연결·서버 동기화가 모두 맞을 때 첫 실제 fix를 즉시 보내고 이후 마지막 실제 fix를 **정지 중에도 3초마다** `location.update`로 보낸다. 첫 fix 전·Sharing 밖·연결 끊김·미동기화·4401·4409에서는 전송하지 않는다. 일반 끊김 동안 전송을 멈추고 같은 세션의 권위 있는 `session.state sharing` 이후 재개한다. 새 세션·종료·`appClosed()`는 이전 내 위치와 heartbeat를 정리한다.
- **서비스와 알림**: `LocationShareService`는 Sharing·위치 권한 조건에서만 `foregroundServiceType="location"`으로 실행한다. ongoing 알림은 **"엄마 차와 위치 공유 중"**과 **"끝내기"** 액션이며, 액션은 화면과 같은 수동 종료 경로를 사용한다. Android 13 이상 알림 권한 거부는 위치 정밀도와 독립적으로 첫 fix 전부터 안내한다. 최신 캡처에서 두 안내의 독립 표시를 확인했다. 알림 거부만으로 coarse의 서비스 실행 자격을 바꾸지 않는다. 정상 시작한 Sharing 서비스는 앱이 뒤로 가거나 화면이 꺼져도 GPS를 계속 구독한다. 종료·4401·4409에서는 서비스·GPS·heartbeat를 멈춘다.
- **API 34 시작 제한**: 백그라운드 WebSocket에서 수락·공유 복원을 받았다는 이유만으로 위치 FGS를 자동 시작하지 않는다. Activity가 보일 때 실행 조건을 다시 확인하고 시작·재시도한다. 시작 제한·보안 예외는 안내로 처리한다. 이미 실행 중인 정상 Sharing 서비스의 백그라운드 GPS 지속과 구분한다.
- **실제 지도와 HUD**: P4 `SharingMap.kt`는 차와 같은 **osmdroid 6.1.20(OpenStreetMap)**이며 User-Agent·`CopyrightOverlay`·`clipToBounds`를 적용한다. 양쪽 지도는 같은 WGS84 차·탑승자 좌표를 쓰고 차는 네이비 `#002C5F`, 탑승자는 시안 `#00AAD2`다. 마커가 보이도록 범위를 맞추고 출처를 가리지 않는다. `CopyrightOverlay.setTextSize(11)`로 density에 따른 출처 잘림을 수정했다. 위치 없음 배너는 양쪽 위치가 모두 없을 때만 표시하며, 최신 권한 거부 캡처에서 단일 실제 차 마커가 가려지지 않음을 확인했다. **지도 아래** HUD의 거리는 내 GPS→차 직선거리, 화살표는 차 방위−유효 GPS course인 "이동 기준", ETA는 **수신 `peer.eta`만** 사용한다. 폰에서 ETA를 추정하지 않는다. 부족한 값은 `—`이며 좌표 숫자·지도 준비 문구·샘플 값은 표시하지 않는다. 기존 로컬 그림은 `PreviewSharingMap.kt`로 옮겨 debug 미리보기에서만 사용하며 GPS를 수집하지 않는다.
- **최근 앱 제거**: 실행 중인 서비스의 `onTaskRemoved` → `PhoneSessionManager.appClosed()`는 활성·연결·동기화 상태에서 `session.end(app_closed)`를 최선 노력으로 보내고 GPS·heartbeat·소켓과 자동 재시도를 멈춘다. 전송 큐 수락은 서버 수신 보장이 아니다. 오프라인 종료는 영구 큐에 넣지 않으며, 다시 열면 서버 상태로 복원한다. 현재 서버는 reason을 읽지 않고 항상 `session.ended(manual)`을 알린다.
- **종료 보장의 경계**: Android 강제 종료(force-stop)·작업 관리자 Stop은 콜백을 보장하지 않는다. Requesting·위치 권한 거부 상태에는 위치 FGS가 없으므로 최근 앱 스와이프의 즉시 종료도 보장하지 않는다. 종료가 전달되지 않으면 서버는 Sharing의 마지막 위치 수신(없으면 승인) 후 **2분 초과**를 만료 검사 때 `peer_lost`로 처리한다. Requesting은 생성 후 5분 이상이면 `request_timeout`이다. 타임아웃은 PROTOCOL.md 4절을 따른다.

## 6. P1/P2 검증 이력과 UI 재설계 검증 상태

이 절 전체는 P3 이전의 검증 이력이다. 당시 기본 화면·release 안내·미리보기 복원 동작을 현재 P3의 기본 진입이나 실행 결과로 해석하지 않는다.

- **UI 재설계 최종 빌드 — 메인 제공 근거**: `:phone:testDebugUnitTest :phone:assembleDebug :phone:lintDebug` 오프라인 통합 실행 BUILD SUCCESSFUL(19초·55개 태스크: 실행 14·최신 상태 41). 테스트 **24개를 모두 재실행·통과**했다(기존 `PhoneViewModelTest` 16개 + 새 `SharingCompassTest` 8개). 두 테스트 XML의 시각은 `2026-11-07T12:31:06Z`이며 실패·오류·건너뜀은 모두 0이다. lint 보고서는 오류 0·기존 경고 8개, 최종 debug APK 설치는 Success다.
- **독립 소스·시각 검토 PASS**: 리뷰어 Zeno가 필수 지적 2건의 해결을 확인했다. 320dp·글꼴 배율 1.3에서 차 라벨과 HUD가 겹치거나 잘리지 않고, 포커스 중 코드 `012345`와 커서가 보이며, 스크롤로 종료 버튼에 접근함을 네이티브 PNG로 확인했다.
- **최종 APK 입력·상태 QA PASS**: `Minji`·`012345`를 실제 입력해 제출한 뒤 홈의 이름 인사를 확인했다. 정보 없음에서는 차·아이 마커와 거리·방향·ETA 값이 남지 않았다. 근접 20m에서 종료 확인창의 "계속 보기"는 근접 화면으로 돌아왔고, "위치 공유 끝내기" → "미리보기 종료"는 "종료 문구 미리보기"·"위치공유를 끝냈어요"를 표시했다.
- **회전·스크롤 QA PASS**: 가로 화면에서 근접 지도·HUD·실내등 안내를 확인하고 추가 스크롤로 "위치 공유 끝내기"에 접근했다. 검증 후 회전은 자동/세로, 글꼴은 1.0, density는 기본값으로 복구했다.
- **P2 검토 반영 후 재검증**: 공유 지도 높이는 420dp에서 약 566dp, 근접은 380dp에서 약 488dp로 늘어나 기본 폰의 남는 공간을 채웠고 종료 버튼 위치는 유지됐다. `p2-review-sharing.png`·`p2-review-nearby.png`로 확인했다. 320dp·글꼴 1.3·가로 화면에서 지도/HUD 표시와 스크롤 후 종료 버튼 접근, 정보 없음의 마커·측정값 숨김, 종료 확인창의 계속 보기를 새 APK에서 다시 확인했다(`build/p2-phone-qa/p2-review-*.png`).
- **네이티브 PNG 근거**: `build/p2-phone-qa/` 아래 `redesign-{pair,home,sharing,nearby,no-data}.png`, `redesign-code-editor.png`, `redesign-home-filled.png`, `redesign-sharing-320-large.png`, `redesign-nearby-320-large-bottom.png`, `redesign-nearby-landscape.png`, `redesign-nearby-landscape-bottom.png`.
- **검증 경계**: 위 결과는 메인과 독립 리뷰어가 제공했으며 문서 작성 담당은 빌드·기기 QA를 실행하지 않았다. 로컬 예시 UI 검증이며 실제 서버(P3)·GPS/위치 서비스(P4)·지도 타일·release APK 런타임·API 26 설치/런타임은 포함하지 않는다.

아래는 UI 재설계 전 P1/P2 검증 이력이다. 기존 16개 테스트·화면 캡처·lint 결과를 새 UI의 최종 검증으로 해석하지 않는다.

- **P1 완료**: 11/07 기존 `Medium_Phone_API_36.0`(API 36, Google APIs Play Store, arm64)을 추가 다운로드 없이 실제 부팅했다. `adb devices -l`에서 차 `emulator-5554`와 폰 `emulator-5556`이 동시에 device였고 폰 `sys.boot_completed=1`을 확인했다. 이는 AVD 검증이며 P2 앱 화면 검증은 아니다.
- 실행: `~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.0 -port 5556 -no-snapshot-load`. 이미 부팅했다면 재실행하지 않는다. 실제 serial은 `adb devices`로 확인하고 모든 명령에 `-s`로 지정한다.
- **release URL 가드 확인**: 11/07 메인에서 JBR 21로 `./gradlew :phone:assembleRelease --dry-run --offline --no-daemon --console=plain`을 실행해, 주소 미설정 시 종료 코드 1과 `release 빌드에는 maejung.serverUrl(local.properties 또는 -P)이 필요합니다` 오류를 확인했다. 예상한 설정 차단이며 release APK는 만들지 않았다. UI의 release 미리보기 차단 검증과는 별개다.
- **P2 완료 — 메인 QA 제공 근거**: phone 16개 테스트 GREEN·debug 조립 성공 후 폰 `emulator-5556`에 APK 설치 Success·`com.maejung.phone/.MainActivity` 실행을 확인했다.
- **화면 표시**: 연결·홈·공유·요청 대기·정보 없음·근접을 캡처해 눈으로 확인했다. 스크롤 메뉴에서 종료 6가지를 각각 선택해 UIAutomator의 실제 보이는 문구가 3절 표와 정확히 일치함을 검증했다(`build/p2-phone-qa/ended-*.json/png`). 요청 시간 초과·연결 끊김의 긴 문구는 이미지에서 줄바꿈도 확인했다.
- **입력·키보드·뒤로 가기**: 빈 입력 오류와 5자리 `01234` 오류를 확인하고 `012345`로 진행했다. `Minji` 입력을 유지한 채 키보드가 열린 화면에서 주 버튼까지 스크롤하고 홈으로 전환했다. 진행 중 시스템 뒤로 가기의 종료 확인창에서 계속 보기가 동작했다.
- **회전·재생성**: 세로→가로에서 `Minji`/`012345` 입력 유지, 주 버튼 스크롤 접근·가로 메뉴 동작을 확인했다. 근접 선택 후 세로로 돌아와 20m 상태를 유지했다. 백그라운드 프로세스를 종료해 PID 7001이 사라진 뒤 PID 8616으로 다시 시작했을 때도 20m 근접 상태가 복원됐다(`nearby-process-restored` 캡처·20m 단언). 이는 화면 미리보기 복원이며 토큰 영구 보관·위치 서비스 검증이 아니다. 검사 후 에뮬레이터 설정은 `accelerometer_rotation=1`, `user_rotation=0`으로 복구했다.
- **lint**: 온라인 실행 BUILD SUCCESSFUL(28초), 오류 0·경고 8개(targetSdk 34, 기존 고정 의존성 5건, dataExtractionRules, 아이콘 누락). 최초 offline 실행은 lint 도구 캐시 부재로 실패했고 온라인에서 고정 도구를 받은 뒤 성공했다. 경고 0개나 의존성 버전 갱신으로 설명하지 않는다.
- **검증 경계**: release URL 미설정 차단은 위 dry-run, release 미리보기 비활성화는 소스·단위 테스트에서 확인했다. release APK 생성·런타임 QA, P3/P4 실제 통신·위치 공유, 실제 발화 STT, Docker·Railway·제출 APK는 이 P2 검증에 포함하지 않는다.
- **검증 후 정리**: 메인은 이번 작업에서 실행한 headless 폰 `emulator-5556`만 `emu kill`로 종료했다. 차 AVD는 그대로 두었으며 폰 APK 설치 상태는 AVD에 남아 있다. 위 동시 부팅·실행 결과는 검증 당시 기록이다.
- P3/P4 구현 후 위치 주입 예: `adb -s <폰에뮬레이터serial> emu geo fix <경도> <위도>`(경도 먼저). P2는 GPS를 읽지 않는다.
- 폰 debug의 서버 주소 설정은 `10.0.2.2:8080`이지만 실제 서버 연결·차 알림·양쪽 위치 표시는 P3/P4 이후 검증한다.

## 7. P3 검증 결과

문서 작성 담당은 빌드·테스트·서버·에뮬레이터를 실행하지 않았다. 아래는 P3 완료 커밋 `f5c7502`까지 메인이 제공한 자동 테스트와 당시 최신 APK의 실제 서버·차/폰 에뮬레이터 검증 이력이다. P3 기능·네이티브 검증을 완료했고 소스·테스트·Manifest의 독립 검토도 승인됐다. 중간 실행 이력과 최종 결과를 구분하며 P4 결과로 대체하지 않는다.

- **이전 중간 결과**: 첫 폰 경계 검증에서 69개 통과, 실패·건너뜀 0(API 8·transport 12·wire 3·Prefs 4·LiveState 2·실제/미리보기 controller 16·기존 P2 ViewModel 16·나침반 8). 세션 매니저 31개 구현 전의 결과이며 아래 새 통합 실행과 구분한다.
- **수정 전 통합 검증 이력**: `--rerun-tasks` 실행이 21초·81개 태스크 실행으로 성공했다. 당시 phone 103개 + app 50개 + protocol 9개 = 162개, 실패·오류·건너뜀 0이었다. 이후 요청 응답 기한 문제를 수정했으므로 현재 폰 결과는 다음 재실행을 기준으로 한다.
- **메인 제공 최신 자동 검증**: 응답 기한 수정 후 폰 **111개**(세션 매니저 42개 포함), 실패·오류·건너뜀 0이다. 폰 테스트·debug 조립·lint의 새 실행은 **23초·55개 태스크 실행**으로 성공했다. 직전 새 실행의 변경 없는 app **50개**·protocol **9개**와 합하면 **170개**이며, 세 모듈을 이번 한 번에 모두 재실행한 결과라는 뜻은 아니다. lint는 **오류 0·경고 10개**(기존 8개 + UseKtx 2개)다. 서버 테스트의 새 결과를 이 합계에 포함하지 않는다.
- **release URL 설정 검사**: 메인이 dry-run에서 주소 누락과 HTTP 주소는 예상대로 종료 코드 1, 유효한 HTTPS 주소는 태스크 그래프 구성 성공을 확인했다. release APK를 빌드·설치하거나 런타임을 검증한 결과는 아니다.
- **메인 제공 실제 기기 확인**: 수정 후 최종 debug APK를 폰 `emulator-5556`에 설치해 Success를 확인했다. 로컬 서버와 차 `emulator-5554`를 사용한 네이티브 화면 캡처에서 404 페어링 실패 후 입력 유지, 실제 페어링 후 홈, 요청 대기·requested 복원, 수락 후 실제 수신 차량 좌표·중립 나침반 표시·sharing 복원을 확인했다. 폰의 직접 종료는 양쪽 종료 안내와 3초 뒤 홈 복귀를, 차의 거절은 폰의 rejected 종료 안내를 확인했다.
- **4409와 명시적 재연결**: 같은 자격의 다른 연결로 폰을 대체해 REPLACED를 확인했고, 15초 넘게 자동 재접속하지 않았다. 폰의 "다시 연결"로 재접속한 뒤 검증용 소켓이 4409로 닫힘을 확인했다. 근거는 `phone-replaced-4409.png`, `phone-replaced-still-stopped.png`, `phone-reconnected-explicit.png`와 `.omc/qa/p3-20261107/terminal-4409.json`이다.
- **서버 재시작과 자동 동기화**: REQUESTED에서 메인이 소유한 서버 실행 터미널만 종료·재실행했다. 폰은 오프라인 동안 활성 요청을 유지했고 자동 재연결 뒤 `session.state none`으로 유실 세션을 정리해 연결된 홈으로 돌아왔다. 8080 포트의 프로세스를 일괄 종료하지 않았다. 근거는 `phone-server-restart-sync.png`, `phone-server-restart-home.png`다.
- **4401과 자격 삭제**: 폰의 보관 토큰을 무효값으로 교체해 실제 서버의 4401을 확인했다. 폰은 올바른 재연결 안내와 Unpaired 화면으로 돌아왔고 검증 JSON에서 `pairingCleared=true`를 확인했다. 실제 토큰 값은 문서에 기록하지 않는다. 근거는 `phone-unauthorized-4401.png`와 `.omc/qa/p3-20261107/terminal-4401.json`이다.
- **미리보기 격리와 기본 진입**: debug에서 명시적으로 미리보기에 진입해 로컬 요청을 실행했으며 차는 Idle을 유지해 실제 요청이 전송되지 않음을 확인했다. 폰을 강제 종료·재실행하면 저장된 미리보기 대신 실제 LIVE·Unpaired로 시작했다. 근거는 `phone-preview-isolated.png`, `car-idle-during-preview.png`, `phone-live-after-preview-restart.png`다.
- **최종 APK 정상 흐름 재확인**: 위 예외 검증 후 새 차 화면 코드로 다시 페어링하고 폰 요청 → 차 UI 수락 → 폰 공유 화면의 실제 수신 차량 좌표·중립 나침반 → 폰 직접 종료·양쪽 종료 안내를 다시 확인했다. 근거는 `phone-final-paired-home.png`, `phone-final-sharing.png`, `phone-final-ended-manual.png`, `car-final-ended-manual.png`, `phone-final-home.png`다. PNG는 `build/p3-phone-qa/emulator-5556/`와 `emulator-5554/`에 있으며 문서에 실제 연결 코드를 기록하거나 이미지를 삽입하지 않는다.

| 항목 | P3 당시 기록 |
|---|---|
| REST·토큰 저장·WebSocket·상태 전이·실제/미리보기 경계 단위 테스트 | 응답 기한 수정 후 phone 111개 통과, 세션 매니저 42개·기존 P2 테스트 포함 |
| `:phone:testDebugUnitTest :phone:assembleDebug :phone:lintDebug` | 성공, lint 오류 0·경고 10개 |
| 로컬 서버 + 차 `emulator-5554`의 코드 → 폰 `emulator-5556` 실제 페어링 | 메인 확인, 404 입력 유지·연결 후 홈 캡처 있음 |
| 폰 요청 → 차 수락 → 양쪽 직접 종료·3초 홈 복귀, 차 거절 수신 | 메인 확인, 네이티브 캡처 있음 |
| `session.state requested`·`sharing` 복원 | 메인 기기 확인, 각 복원 화면 캡처 있음 |
| 실제 4409·명시적 다시 연결 | 확인, 15초 초과 자동 재접속 중단·사용자 재연결·검증용 소켓 4409 종료 |
| 일반 재연결·서버 재시작 | 확인, 활성 요청 유지 후 자동 재연결·`none` 동기화·연결된 홈 복귀 |
| 실제 4401 | 확인, 올바른 안내·Unpaired 전환·보관 자격 삭제 |
| 미리보기 경계·앱 재시작 | 확인, 로컬 요청 중 차 Idle·재실행 시 실제 LIVE 진입 |
| 예외 검증 후 최신 APK 전체 정상 흐름 | 확인, 재페어링·요청·차 수락·공유·양쪽 직접 종료 |
| release APK 런타임 | 미검증 |
| API 26 설치·런타임 | 미검증 |
| GPS·위치 권한·위치 서비스·양쪽 실제 지도 | P4 범위, P3 검증에 포함하지 않음 |

## 8. P4 검증 결과

**P3 `f5c7502` 기반 P4 구현과 합의한 debug 범위의 main QA를 완료했으며 TASKS.md의 P4를 체크한다.** 아래는 main 제공 실행·네이티브 근거이며 문서 작성 담당이 테스트·빌드·기기를 실행한 결과가 아니다. 7절의 phone 111개·합계 170개와 P1/P2 결과·캡처는 과거 이력으로 유지한다. QA 산출물은 `build/p4-phone-qa/`이며 main이 `native-qa.json`과 전체 `server-runtime-final.log`(97,721바이트)를 최종 저장했다. 변경·보호 파일 목록은 `.omc/qa/p4-20261107/final-manifest.json`·`changed-files.md`에서 관리한다.

| 검증 항목 | main 제공 결과 |
|---|---|
| P4 초기 통합 테스트·빌드 | 27초·81개 태스크 실행. phone 175 + app 50 + protocol 9 = **234개**, 실패·오류·건너뜀 0. `green-integration`은 course 회귀 수정 전 이력 |
| GPS course 회귀 RED → GREEN | 수정 전 9개 중 8개가 실제 `AssertionFailedError`로 실패. 유효 속도 임계 조건·GPS 변위 fallback 수정 후 **28초·81개 태스크 실행**, phone **184** + app **50** + protocol **9** = **243개** 통과, 실패·오류·건너뜀 0 |
| 안내·배너 수정 후 최종 전체 실행 | **29초·81개 태스크 실행**, phone **184** + app **50** + protocol **9** = **243개 GREEN**, 실패·오류·건너뜀 **0**. 보관 기록 **`green-notices`**, lint **오류 0·경고 12개** |
| 마지막 상수 정리 후 증분 실행·설치 | **27초·실행 15·UP-TO-DATE 66**, phone **184개 재실행 통과**. app 50개·protocol 9개는 직전 새 전체 실행 결과를 유지한 UP-TO-DATE이며 243개 전부를 다시 실행했다는 뜻은 아님. lint 오류 0·경고 12개, 최신 debug APK 설치 **Success** |
| 위치 권한 거부·실제 요청·Sharing | 거부 후 실제 차에 요청 도착·Sharing 진입, 내 GPS·위치 FGS 없음. 최신 **`final-permission-denied-marker`**에서 네이비 차 마커가 가려지지 않고 권한·알림 안내는 지도 밖에 표시되며 HUD는 미확인값 |
| coarse-only·알림 거부 | **fine=false/coarse=true·notification denied**에서 Sharing·FGS 실행. 초기 LOW_POWER·10분 간격/GNSS 꺼짐 상태로 fix가 지연됐으나 **`final-coarse-notification-denied`**에서 실제 난독화 coarse GPS와 화면 **1286m**, 정밀도·알림 차단 안내 둘 다 확인. 영구 수신 불가나 적시·정밀 GPS 보장을 뜻하지 않음 |
| 최신 정상 공유·수신 ETA | fine 복구 후 **`latest-sharing-phone`·`latest-sharing-car`**: 폰 **258m**·차 **257m**, 폰 정지 course **`—`**, 주기 업데이트 수신 후 양쪽 ETA **약 1분** |
| 같은 fix로 정렬한 양쪽 지도 | **`final-sharing-phone-aligned`·`final-sharing-car-aligned`**: 양쪽 **189m**, 폰 정지 course **`—`**, 수신 ETA **44초**·차 분 단위 표시 **1분**. 앞선 **`final-academy`**도 정지 course `—`·258m·ETA 1분 확인 |
| 지도 출처·작은 화면·큰 글꼴 | `CopyrightOverlay.setTextSize(11)` 수정 후 일반 배율 및 **320dp·글꼴 1.3**에서 전체 출처 문구 표시, 스크롤 후 하단 종료 버튼 접근 확인. 글꼴 **1.0**·density **420**으로 복구 |
| 화면 꺼짐·정지 heartbeat | **141.237초** 동안 3초 heartbeat 지속, `peer_lost` 없음 |
| 백그라운드 수락·화면 복귀 | Home으로 앱을 뒤에 둔 뒤 수락 시 위치 FGS 없음. 화면 복귀 후 location FGS 타입 **0x8**·GPS 시작 |
| 실제 GPS 이동·Nearby·실내등 | 실제 폰 GPS를 **15단계·3초 간격** 이동. **23:39:52.553, 34m**에서 Nearby·폰 실내등 ON 안내 확인. 대역 스크립트 검증과 구분 |
| RR 문 탑승 종료·실내등 복구 | Virtual Console RR 열림 **23:40:30**·닫힘 **23:40:35**, 서버 `boarded` **23:40:35.876**, 차 실내등 **OFF** 복구·폰 홈 복귀·위치 서비스 제거 |
| 실제 최근 앱 제거·app_closed | **150ms** 스와이프로 실제 카드 제거, 서비스 `onTaskRemoved`·`app_closed` 콜백 로그 **23:43:21.498**. 서버 `manual` 종료 로그 **23:43:21.089**도 main 확인. 각 로그의 기록 시각이며 일반적인 콜백 보장을 뜻하지 않음 |
| 최신 알림 끝내기 | **23:56:48.887** 서버 `session.end`·`session.ended(manual)` 확인. **`latest-ended-home`·`latest-ended-car`** 캡처와 `dumpsys`의 FGS **(nothing)** 확인. 앞선 알림 종료에서도 양쪽 manual 종료·서비스 제거 확인 |
| 보호 파일·변경 목록 | main 제공 **37개 변경 파일·보호 파일 80개 일치**, 범위 밖 추가 파일 없음. HEAD **`f5c7502`**·브랜치 유지·diff check 정상. 최종 문서 저장 후 main이 manifest를 재생성 |
| QA signoff·독립 검토 | 합의한 P4 debug 범위 **main QA 완료**. 독립 검토 결과는 최종 QA 근거와 함께 관리한다 |

**검증 경계**: 실물 기기·API 26 설치/런타임·release 런타임은 미검증이다. P4의 재연결·새 세션·4401/4409 정리·오프라인 종료·타임아웃은 자동 회귀와 5절의 구현 경계로 구분하며 새 네이티브 검증을 주장하지 않는다. coarse는 실제 fix를 받았지만 이 에뮬레이터의 초기 지연이 있어 적시·정확한 이동에는 fine이 필요하다. force-stop·작업 관리자 Stop·위치 FGS 없는 Requesting/권한 거부 상태의 최근 앱 제거 콜백은 보장되지 않고, 오프라인 종료는 큐에 남기지 않으며 서버 heartbeat 2분 초과 만료가 대안이다. 보호된 차 앱에서 두 점이 0m로 겹칠 때 세계 범위로 확대되는 기존 현상은 이번 범위 밖이며 변경하지 않았다.

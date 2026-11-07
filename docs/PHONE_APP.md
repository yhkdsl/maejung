# 마중 폰 앱 (탑승자용)

탑승자(학원생 등)가 쓰는 일반 Android 앱이다. **2026-11-07, 기존 HEAD `74821e3`와 작업 중인 P2 변경 기준**으로 `:phone` 모듈·빌드 설정·Manifest·오프라인 UI를 구현했다. 기존 P2 테스트·debug APK·폰 AVD 검증 이력과 승인된 네이티브 Compose UI 재설계의 검증 상태는 6절에서 구분한다. 2~3절은 현재 P2 모듈·화면·로컬 미리보기 구현, 4절은 미구현 P3 통신 상태 머신, 5절은 미구현 P4 위치·서비스 설계다. P2 미리보기를 실제 페어링·픽업 요청·위치 공유 성공으로 계산하지 않는다.

메시지는 현재 서버·공유 모듈 구현을 정리한 [PROTOCOL.md](PROTOCOL.md)가 기준이다. P2는 실제 REST·WebSocket, 토큰 영구 보관, GPS, 권한 요청, 위치 포그라운드 서비스를 구현하지 않는다. 빌드 공통 규칙은 [CAR_APP.md](CAR_APP.md) 1절을 참고한다.

## 0. 현재 동작하는 대역과 검증 범위

- 서버·차와 실제 통신하는 탑승자 역할은 계속 `scripts/fake-rider.mjs`가 수행한다. Node 22 이상 내장 WebSocket을 사용하는 개발용 스크립트다. 폰 P2 오프라인 UI와는 별개다.
- 차 화면에서 발급받은 코드를 넣어 실행한다:

  ```bash
  node scripts/fake-rider.mjs <6자리코드> [서버주소=http://localhost:8080] [이름=민지]
  ```

- `POST /api/pairings` → 받은 riderToken으로 WebSocket 연결 → 첫 `session.state` 수신 시 `pickup.request` → `pickup.accepted` 후 2초 간격 위치 전송 순서다. 학원에서 차까지 15단계로 접근한 뒤 도착 좌표를 계속 보낸다. 실제 휴대폰 GPS를 읽지 않는다.
- 토큰·이름 영구 보관, 재연결, Android UI·권한 요청·포그라운드 서비스는 구현하지 않는다. `session.ended`나 `error`를 받으면 전송을 멈추고 소켓을 닫는다. 연결이 닫히면 프로세스가 종료되므로 새 실행에는 새 연결 코드가 필요하다.
- 11/07 기존 에이전트 기록에는 이 대역과 로컬 서버·차 에뮬레이터의 페어링부터 탑승 종료까지 검증이 있다. 이는 폰 앱의 실제 통신 검증이 아니다. 아래 P2 실행 근거는 메인 QA가 제공했고 문서 작성 담당은 별도로 에뮬레이터·빌드를 실행하지 않았다.
- 앞선 세션의 JBR 21·offline·`--rerun-tasks` 결과는 app 46 + protocol 9 + server 34 = 89개, 실패 0이다. 이 이전 결과에는 phone 테스트가 포함되지 않는다.
- 기존 P2 메인 검증 이력(UI 재설계 전): phone 테스트 **16개 통과·debug 조립 성공(7초)**, JBR 21로 `:protocol:test :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --offline --no-daemon --console=plain` 성공(12초·47개 태스크 실행). app 46 + protocol 9 = 55개, 실패·오류·건너뜀 0이다. 서버는 소스 변경 없이 재실행하지 않았고 34개는 이전 결과다. 폰 화면·복원 근거와 UI 재설계의 새 검증 상태는 6절에 정리한다.
- UI 재설계 최종 검증(메인 제공): `:phone:testDebugUnitTest :phone:assembleDebug :phone:lintDebug` 오프라인 통합 실행 **성공(19초·55개 태스크: 실행 14·최신 상태 41)**. 테스트 24개를 모두 재실행해 통과했고 lint는 오류 0·기존 경고 8개, 최종 debug APK 설치는 Success다. API 27 테마를 기본 리소스와 `values-v27`로 분리했고 불필요한 null 검사도 제거했다. 독립 소스·시각 검토와 네이티브 AVD QA는 PASS이며 상세는 6절이다. API 26 설치·런타임은 미검증이다.
- Docker는 설정 작성 후 Hub timeout으로 image build 성공 미확인(S4 검증 대기), Railway는 배포 미진행이다. 차 앱의 음성 코드(A6)는 구현돼 있으나 실제 발화 STT는 사용자 검증 대기다.

## 1. 1차 범위

- **1차(11/15)는 Android만.** iOS는 만들지 않는다.
- 화면 3개: 연결, 홈(요청), 마중 중. 그 밖에 요청 대기·종료 문구는 홈 화면 안의 상태 표시로 처리한다.
- 승인된 UI를 네이티브 Compose로 적용했다. 공유 화면에는 북쪽 고정 로컬 예시 지도와 거리·이동 기준 나침반·ETA HUD, 실내등 안내가 있다. 지도는 Canvas로 그린 도식이며 실제 GPS·지도 타일 연동은 미구현이다. Google Maps API 키나 외부 지도 SDK를 사용하지 않는다.
- 차 이름은 1차에서 "엄마 차"로 고정한다(연결할 때 입력받는 건 PT 단계).
- P2는 디버그 오프라인 UI, P3는 실제 페어링·요청·재연결·토큰 보관, P4는 위치 공유 서비스로 나눠 구현한다. P2 화면의 거리·ETA·실내등은 샘플이다.

## 2. P2 모듈과 빌드

- 루트 `settings.gradle.kts`가 `:app`·`:protocol`·`:phone`을 포함한다. `phone/`의 namespace·applicationId는 `com.maejung.phone`, minSdk 26, **compileSdk 35**, targetSdk 34, JVM 17이다.
- 일반 휴대폰 앱이다. `android.hardware.type.automotive`를 선언하지 않는다.
- 기존 AGP 8.10.1·Kotlin 2.1.21·Compose BOM `2025.05.01`·Material3와 `project(":protocol")`을 재사용한다. Activity Compose `1.10.1`, Lifecycle Compose `2.9.0`, Core KTX `1.16.0`, 차 앱과 같은 Coroutines Android `1.10.2`를 고정한다. P2에는 OkHttp·GMS 위치·Pleos 의존성을 추가하지 않는다.
- `BuildConfig.SERVER_URL`은 debug `http://10.0.2.2:8080`, release는 `-Pmaejung.serverUrl` 우선이며 없으면 `local.properties`의 같은 속성이다. 주소가 없으면 release 태스크가 실패한다. 이 설정이 있어도 P2 통신 코드는 없다.
- Manifest의 런처는 `com.maejung.phone/.MainActivity`, `windowSoftInputMode="adjustResize"`다. P2에는 `INTERNET`·위치·알림·포그라운드 서비스 권한과 서비스 선언이 없다.
- debug APK: `phone/build/outputs/apk/debug/phone-debug.apk`. 11/07 조립·폰 AVD 설치·런처 실행을 확인했다. 실행 명령은 [README](../README.md)의 폰 오프라인 미리보기를 참고한다.

## 3. P2 화면과 로컬 상태 미리보기 (구현 완료)

아래 파일은 현재 소스에 존재한다. `PhoneState`의 `PreviewScene`과 `PhoneViewModel`은 로컬 화면 상태만 다루며 서버 세션과 별개다. `MainActivity`가 `BuildConfig.DEBUG`를 전달하고 `PhoneApp`도 이를 다시 검사한다.

```
phone/src/main/java/com/maejung/phone/
├── MainActivity.kt               런처, BuildConfig.DEBUG를 미리보기 경계로 전달
├── session/
│   └── PhoneState.kt             입력·검증 오류·PreviewScene·SharingInfo
└── ui/
    ├── PhoneApp.kt               마중 브랜드·상단 배지/메뉴·화면 선택·종료 확인
    ├── PairScreen.kt             이름·코드 단일 편집기/여섯 칸 표시·형식 검증
    ├── HomeScreen.kt             차량 일러스트·요청·요청 대기·종료 안내
    ├── SharingScreen.kt          지도·거리/나침반/ETA HUD·실내등 안내·공유 종료
    ├── SharingMap.kt             북쪽 고정 로컬 도식·정차한 차/아이 마커
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
- 두 공유 샘플은 북쪽 기준 차 방위 80°·이동 방향(course) 35°이므로 나침반 화살표는 이동 기준 오른쪽 앞 45°다. 나침반의 12시는 이동 방향이며 지도 북쪽·휴대폰 방향과 별개다. course가 없으면 나침반은 `—`로 표시하고 화살표와 지도 위 이동 방향 표시를 숨긴다. 실제 GPS course 수집·정지 시 유효성 판정은 P4다.
- 공유·근접 지도는 최소 높이를 유지하면서 화면에 남는 세로 공간을 채운다. 지도·실내등 안내와 종료 버튼 사이에는 정해진 여백만 두며, 작은 화면·큰 글꼴·가로 화면은 내용 전체를 스크롤한다.
- 정보 없음은 "위치 정보를 기다리고 있어요"와 "위치 정보가 없어 차 위치를 표시할 수 없어요."를 표시한다. 지도 바탕만 남기고 차·아이 마커와 이동 방향·나침반 화살표를 모두 숨기며 거리·나침반·ETA는 `—`, 실내등은 "실내등 정보를 확인할 수 없어요."로 표시한다. 이전 근접·공유 샘플 값은 남기지 않는다.
- 화면·이름·코드·입력 오류 표시 여부는 `SavedStateHandle`에 보존하고 `StateFlow`로 화면에 전달한다. `PhoneComponents`의 페이지는 스크롤 가능하며 `PhoneApp`은 시스템 영역·키보드 여백을 적용한다. 회전·키보드·프로세스 재생성 QA는 6절의 범위로 확인했다. 토큰 영구 보관과 네트워크 세션 복원은 P3다.
- 요청 대기·공유의 종료 버튼 또는 시스템 뒤로 가기는 "미리보기를 끝낼까요?" 확인창을 연다. "미리보기 종료"를 누르면 manual 종료 예시, "계속 보기"는 현재 화면 유지다. 종료 상태는 "홈 미리보기로"를 눌러 돌아오며 자동 3초 복귀는 없다. 홈의 뒤로 가기는 입력을 보존한 채 연결 화면으로 돌아간다.
- 미리보기 메뉴·연결·요청 전환은 `BuildConfig.DEBUG`에서만 허용한다. 디버그 화면과 행동에는 로컬 미리보기임을 표시하며 실제 차에 알림·요청을 보내지 않는다. release는 P3 전까지 연결·요청을 사용할 수 없다는 안내를 보여 주고 미리보기 전환을 허용하지 않는다.

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

release는 "마중" 브랜드·"준비 중" 배지와 실제 연결 서비스 준비 안내를 표시한다. 연결 버튼은 비활성이며 메뉴가 없고 저장된 디버그 장면도 복원하지 않는다. 입력·상태·복원·release 차단 동작 테스트는 `phone/src/test/java/com/maejung/phone/ui/PhoneViewModelTest.kt`의 16개다. 기존 통과 이력과 UI 재설계 후 메인이 제공한 24개 결과는 6절에 구분한다. release 미리보기 차단 근거는 소스·단위 테스트이며 release APK를 설치해 확인한 것은 아니다.

### 종료 문구 6가지

P2는 아래 문구를 홈의 종료 상태로 미리보기한다. 서버의 `session.ended` 수신 처리는 P3이며 P2 표시는 서버 종료 검증이 아니다.

| reason | 문구 |
|---|---|
| `boarded` | "탑승 확인! 위치공유를 끝냈어요" |
| `rejected` | "엄마가 지금은 못 온대요" |
| `manual` | "위치공유를 끝냈어요" |
| `request_timeout` | "응답이 없어요. 다시 요청해 볼까요?" |
| `peer_lost` | "연결이 끊겨서 위치공유를 끝냈어요" |
| `max_duration` | "1시간이 지나 위치공유를 끝냈어요" |

## 4. P3 통신 구조와 상태 전이 (미구현)

통신 계약의 기준은 [PROTOCOL.md](PROTOCOL.md)다. 메시지 형식 변경·추가가 필요하면 문서를 먼저 수정하고 `protocol/` → `server/` → `app/` → `phone/` 순서로 맞춘다. 폰 작업에서 차 앱·서버 코드 변경이 필요하면 사용자에게 변경 필요사항과 영향을 먼저 알리고 해당 코드는 수정하지 않는다.

다음 클래스는 P2에 없고 P3/P4에서 추가할 설계다.

| 담당 | 예정 구현 |
|---|---|
| P3 `MaejungPhoneApp`·`PhoneSessionManager` | Application 범위에서 실제 WebSocket 연결·세션 StateFlow 소유. UI ViewModel은 이 상태 구독 |
| P3 `net/ApiClient`·`WebSocketTransport` | REST 페어링·토큰 인증·메시지 전송·재연결(PROTOCOL.md 3절) |
| P3 `store/Prefs` | riderToken·riderName·vehicleId 영구 보관·인증 실패 시 삭제 |
| P4 `location/FusedLocationSource` | FusedLocationProviderClient, 3초 간격·PRIORITY_HIGH_ACCURACY 설계 |
| P4 `LocationShareService` | Sharing 중 위치 수신·전송과 프로세스 유지, `foregroundServiceType="location"` |

실제 연결을 Activity 범위 ViewModel만으로 소유하지 않는다. P2의 `PreviewScene`은 아래 실제 세션 상태 머신을 대체하지 않는다. OkHttp·GMS 위치 의존성은 해당 작업에서 호환 버전을 고정한다.

아래는 폰 클라이언트 설계다. `pickup.pending`·`pickup.accepted`·`peer.*`·`session.ended` 등 서버 메시지는 구현돼 있지만 이 표의 폰 화면 처리·10초 대기·서비스 시작·토큰 삭제를 수행하는 코드는 없다. 현재 서버의 `session.state`는 상태와 탑승자 정보만 복원하며, 이전 위치·ETA·실내등 값이나 종료 이유는 재전송하지 않는다.

| 현재 | 이벤트 | 다음 | 동작 |
|---|---|---|---|
| Unpaired | 연결 성공(`POST /api/pairings` 201) | Idle | 토큰·이름 저장, WebSocket 연결 |
| Unpaired | 404·400 | Unpaired | "코드를 다시 확인해 주세요" |
| Idle | "데리러 와줘" 버튼 | Requesting(null) | 위치 퍼미션 확인(없으면 요청, 거부 시 Idle에 안내) 후 `pickup.request {riderName}`. **WebSocket이 연결되지 않은 동안 버튼은 비활성**("연결 중…") |
| Requesting(null) | 10초 안에 `pickup.pending`·`error`가 없음 | Idle("연결을 확인해 주세요") | 요청이 유실된 경우 |
| Requesting | `pickup.pending` | Requesting(sessionId) | "엄마 차에 알렸어요" |
| Requesting | `error VEHICLE_OFFLINE` | Idle("차가 꺼져 있어요") | |
| Requesting | `error SESSION_ALREADY_ACTIVE` | Idle("이미 다른 요청이 진행 중이에요") | |
| Requesting | 취소 버튼 | Ended(manual) | `session.end manual` |
| Requesting | `pickup.accepted` | Sharing | LocationShareService 시작 |
| Sharing | `peer.location`, `peer.eta`, `peer.light` | Sharing | 화면 갱신. `peer.light on`이면 "실내등 켜진 차가 엄마 차예요"를 크게 강조 |
| Sharing | 종료 버튼(화면 또는 알림) | Ended(manual) | `session.end manual`, 서비스 중지 |
| Requesting·Sharing | `session.ended(reason)` | Ended(reason) | 서비스 중지 |
| Requesting·Sharing | `session.state none` | Ended(peer_lost) | 서비스 중지 |
| Idle | `session.state requested` | Requesting(sessionId) | |
| Idle·Requesting | `session.state sharing` | Sharing | 서비스 시작 |
| Ended | 3초 뒤 | Idle | |
| 아무 상태 | WebSocket close 4401 | Unpaired | 토큰 삭제 |

종료 문구는 3절의 6가지를 사용한다. P3의 3초 뒤 Idle 복귀와 서버 이벤트 전이는 P2 미리보기 완료 기준에 포함하지 않는다.

## 5. P4 위치 전송과 앱 종료 (미구현)

- **실제 지도 전환**: GPS를 붙일 때 현재 `SharingMap.kt`의 로컬 도식을 차 앱과 같은 **osmdroid(OpenStreetMap)** 지도로 교체한다. 차 앱의 `app/src/main/java/com/maejung/ui/PickupMap.kt`를 참고해 앱 식별 User-Agent와 `CopyrightOverlay`를 적용하고, 지도 컨테이너에 `clipToBounds`를 적용한다. 출처 표시는 HUD에 가리지 않게 유지한다. 같은 세션의 차량·탑승자 WGS84 좌표를 양쪽 지도에 표시하고 시연 때 동일 위치가 보이는지 확인한다. 지도 위 거리·ETA·상대 나침반("이동 기준") HUD는 유지하며, 이동 방향을 12시로 삼는 의미도 유지한다. 현재 P2 지도는 예시 그림이며 실제 타일·GPS 연결은 아직 없다.
- **위치 전송은 Sharing일 때만** 하는 설계다. "3초 또는 10m, 최소 10초마다"는 폰의 예정 규칙이다. 현재 차 앱은 마지막 위치를 3초마다 보내고 대역은 2초마다 보내며, 서버는 수신 간격으로 만료를 판단한다(PROTOCOL.md 3.2·4절).
- 포그라운드 서비스 알림: "엄마 차와 위치 공유 중" + "끝내기" 액션.
- 최근 앱에서 스와이프하면 서비스의 `onTaskRemoved`에서 `PhoneSessionManager`로 `session.end(app_closed)`를 보내고 서비스를 멈추는 설계다. 콜백·전송 성공은 아직 검증하지 않았다. 현재 서버는 받은 reason을 `manual`로 통일한다. 전송되지 않으면 **SHARING에서 마지막 위치 수신(없으면 승인) 후 2분 초과**를 만료 검사 때 `peer_lost`로 처리한다. REQUESTED는 생성 후 5분 이상에 `request_timeout`이다.
- P3에서 통신용 `INTERNET`, P4에서 `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`(33 이상)을 추가할 설계다. "데리러 와줘"를 처음 누를 때 위치 권한을 요청하고 포그라운드 서비스로 공유한다. P2 Manifest에는 이 권한이 없으며 기기에서 위치 권한·서비스 흐름도 검증하지 않았다.

## 6. P1/P2 검증 이력과 UI 재설계 검증 상태

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

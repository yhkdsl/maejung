# 마중 서버 구현 현황

차와 탑승자 사이에서 픽업 요청과 위치를 중계하는 서버. 아래는 `server/`의 현재 구현이며 통신 계약은 [PROTOCOL.md](PROTOCOL.md)에 정리한다. 탑승자용 `phone/`은 P2 오프라인 UI 구현·debug/폰 AVD 검증을 완료했고 실제 서버 연동(P3)·GPS(P4)는 미구현이다. 서버·차 통합의 로컬 대역은 `scripts/fake-rider.mjs`다.

## 0. 구현과 검증 상태 (2026-11-07)

기준 HEAD는 `74821e3`다. 이 문서 수정 중 테스트·빌드·에뮬레이터를 새로 실행하지 않았다.

| 항목 | 상태와 근거 |
|---|---|
| REST·HMAC 토큰·연결 코드·WebSocket·세션 만료 | 소스 구현 완료. 기존 서버 JUnit XML에서 34개, 실패·오류·건너뜀 0 확인 |
| 전체 단위·통합 테스트 | 앞선 세션의 JBR 21·offline·`--rerun-tasks` 결과. app 46 + protocol 9 + server 34 = 89개, 실패 0. 이번 작업에서는 기존 XML을 읽어 확인 |
| P2 추가 중 새 루트 회귀 검증 | 메인에서 JBR 21로 protocol·app 테스트와 차 debug 조립 성공(12초·47개 태스크 실행). app 46 + protocol 9 = 55개, 실패·오류·건너뜀 0. 서버 소스 변경이 없어 server 테스트는 재실행하지 않았으며 34개는 위 이전 결과 |
| 차 앱 debug 조립 | 앞선 세션에서 성공한 결과를 인계받음. 이번 작업에서 재실행하지 않음 |
| 로컬 서버 + 차 에뮬레이터 + 탑승자 대역 | 11/07 기존 에이전트 기록에 코드 발급·페어링·요청·승인·위치·근접 실내등·탑승 종료·서버 재시작 복구가 있음. 이번 작업에서 재현하지 않음 |
| Docker·Railway (TASKS S4) | Dockerfile·설정 작성. Docker Hub 이미지 메타데이터 조회 timeout으로 image build 성공 미확인, **빌드 검증 대기**. Railway 배포 미진행 |
| 실제 발화 STT (TASKS A6) | 차 앱 음성 구현 완료. 인식 준비까지의 11/07 기록은 있으나 실제 발화 인식은 사용자 검증 대기 |
| 휴대폰 앱 | P2 오프라인 UI 완료: phone 16개 테스트·debug 조립·폰 AVD 설치·화면/복원 확인. 실제 REST·WebSocket·토큰 보관 P3·GPS/서비스 P4 미구현. [PHONE_APP.md](PHONE_APP.md)에서 소스·실행 근거와 미래 설계 분리 |

## 1. 현재 구성

- **독립 Spring Boot 서버를 구현했다.** 외부 BaaS를 쓰지 않는다.
- **스택**: Kotlin 2.0.21, Spring Boot 3.4.5, Java 21, Gradle 8.12 wrapper. Dockerfile은 `gradle:8.12-jdk21` 빌드 → `eclipse-temurin:21-jre` 실행의 2단계이며 Railway는 배포 대상으로만 준비했다.
- **DB, Redis, Spring Security, JWT, Firebase는 쓰지 않는다.** 진행 중 세션·연결 코드·연결 레지스트리만 메모리에 둔다. **좌표는 저장하지 않고 중계하며, 세션에는 차·탑승자의 마지막 위치 수신 시각만 저장한다.**
- **로그인 대신 HMAC 서명 토큰**(PROTOCOL.md 1절). 같은 비밀키와 클라이언트의 기존 토큰이 유지되면 서버 재시작 후에도 페어링을 사용할 수 있다. 서버에 차량·가족·토큰 목록은 없다.
- **서버 한 프로세스 전제**다. 세션·코드·소켓을 여러 인스턴스 사이에 공유하는 구현은 없다.

## 2. 위치와 빌드

- 저장소 `server/` 폴더. **Android 빌드(루트 `settings.gradle.kts`)와 별개인 독립 Gradle 프로젝트**다. Kotlin·AGP 버전이 달라 같은 빌드에 넣지 않는다.
- 빌드·실행:
  ```bash
  cd server
  ./gradlew test
  ./gradlew bootRun          # http://localhost:8080
  ```
- JDK 21이 필요하다. 로컬에서는 Android Studio JBR 21(`/Applications/Android Studio.app/Contents/jbr/Contents/Home`)을 그대로 쓴다.

## 3. 의존성

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
}
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.withType<Test> { useJUnitPlatform() }
// Spring Boot 3는 *-plain.jar도 만든다. Dockerfile의 COPY build/libs/*.jar가 파일 두 개를 만나 실패하지 않게 끈다
tasks.named<Jar>("jar") { enabled = false }
```

- Gradle 8.12 wrapper는 `server/gradle/wrapper/`에 이미 포함돼 있다.
- `server/Dockerfile`도 작성돼 있다. 빌드 단계는 `gradle bootJar --no-daemon`으로 테스트를 실행하지 않는다. 위의 `jar { enabled = false }`는 plain jar를 막아 실행 jar 하나만 복사하도록 한다. 기존 `bootJar` 산출 확인과 Docker image build 성공 확인은 별개이며 후자는 대기 중이다.

## 4. 패키지 구조

```
server/src/main/kotlin/com/maejung/server/
├── MaejungServerApplication.kt     @SpringBootApplication, @EnableScheduling
├── config/
│   ├── WebSocketConfig.kt           /ws 에 핸들러 등록. HandshakeInterceptor는 쿼리의 token을 attributes에 옮기기만 한다(거절하지 않음)
│   └── ClockConfig.kt               Clock 빈(테스트에서 고정 시계로 교체)
├── token/
│   └── TokenService.kt              발급·검증. HMAC-SHA256, 비밀키 MAEJUNG_TOKEN_SECRET
├── api/
│   ├── VehicleController.kt         POST /api/vehicles
│   ├── PairingController.kt         POST /api/pairing-codes, POST /api/pairings
│   ├── HealthController.kt          GET /health
│   └── ApiExceptionHandler.kt       {code, message} 오류 응답
├── pairing/
│   └── PairingCodeStore.kt          6자리 코드 → vehicleId, 10분 만료, 1회용 (@Synchronized HashMap)
├── ws/
│   ├── MaejungWebSocketHandler.kt   TextWebSocketHandler. afterConnectionEstablished에서 토큰 검증(실패 시 close 4401) → 등록 → session.state 전송. 메시지 파싱 → SessionService 호출
│   ├── TokenQueryInterceptor.kt     쿼리의 token을 세션 속성으로 옮기기만 한다(거절하지 않음)
│   ├── ConnectionRegistry.kt        Outbox 구현. principal key(vehicleId 또는 riderId) → WebSocketSession, 중복 연결 4409
│   └── Envelope.kt                  Envelope(type, sessionId, ts, payload: Map). payload는 type별 data class 없이 Map으로 다룬다
└── session/
    ├── Outbox.kt                    send(principalKey, envelope), isOnline(key). SessionService가 WebSocket을 모르게 하려는 경계(테스트는 가짜 구현)
    ├── PickupSession.kt             id, vehicleId, riderId, riderName, state, createdAt, acceptedAt, 마지막 위치 수신 시각(차·탑승자)
    ├── SessionService.kt            상태 전이, 중계, 종료 처리 (PROTOCOL.md 4절)
    └── SessionSweeper.kt            @Scheduled(fixedDelay = 5초) 만료 검사
```

## 5. 구현 규칙

- **동시성**: 세션은 `HashMap<sessionId, PickupSession>`, 인덱스 `vehicleId → sessionId`, `riderId → sessionId`. `SessionService`의 공개 메서드(`onConnected`, `handle`, `sweep`) 전체를 `@Synchronized`로 보호한다. 연결 레지스트리는 `ConcurrentHashMap`, 연결 코드 저장소의 발급·소비 메서드는 `@Synchronized`다.
- **WebSocket 송신**: Spring `WebSocketSession.sendMessage`는 스레드 안전하지 않다. `ConcurrentWebSocketSessionDecorator(session, 5000, 64 * 1024)`로 감싸서 등록한다.
- **시간**: `Clock`을 주입받아 쓴다. `System.currentTimeMillis()`를 직접 부르지 않는다(만료 테스트를 위해).
- **로그**: 수신 시 type·sessionId·principal 종류(v/r), 종료 시 sessionId·reason, 송신 실패 시 type·대상 종류 접두사를 기록한다. 정상 중계 payload의 좌표·이름·토큰은 서버 애플리케이션 로그에 남기지 않는다. 개발용 대역 스크립트는 수신 payload와 좌표를 콘솔에 출력한다.
- **포트**: `server.port=${PORT:8080}` (Railway가 PORT를 준다).
- **비밀키**: `application.yml`에는 `maejung.token-secret: ${MAEJUNG_TOKEN_SECRET:local-dev-secret-do-not-use-in-prod-0123456789}` 기본값이 있다. 로컬 실행·테스트는 환경변수가 없으면 이 값을 쓴다. 작성된 `application-railway.yml`에는 `maejung.token-secret: ${MAEJUNG_TOKEN_SECRET}`만 있다. 배포 시 `SPRING_PROFILES_ACTIVE=railway`를 지정해야 하며, 이 프로필에서 비밀키 환경변수가 빠지면 기동이 실패하도록 구성돼 있다. 실제 Railway 기동은 미검증이다.
- **토큰 페이로드**: PROTOCOL.md 1절. id는 `v_`/`r_`/`s_` 접두사 + 하이픈을 뺀 UUID 앞 12자.
- **탑승자 이름**: 토큰에 없다. `pickup.request` payload의 `riderName`을 세션에 담아 `pickup.requested`·`session.state`에 쓴다.
- **세션 인덱스로 찾기**: 클라이언트 메시지의 `sessionId`는 무시하고 `vehicleId → sessionId`, `riderId → sessionId` 인덱스로 찾는다(PROTOCOL.md 3절).
- **요청 처리 순서**: `pickup.request`의 이름 trim·1~20자 검사(`BAD_MESSAGE`) → 차 연결 검사(`VEHICLE_OFFLINE`, 세션 생성 안 함) → 차·탑승자 진행 중 세션 검사(`SESSION_ALREADY_ACTIVE`) → 세션 생성 → 차에 `pickup.requested`, 탑승자에 `pickup.pending`.
- **위치 저장**: **좌표는 저장하지 않고 중계만 한다.** 만료 판단에 필요한 차·탑승자 각각의 마지막 위치 수신 시각만 세션에 둔다.
- **SHARING이 아닐 때** 오는 `location.update`·`eta.update`·`vehicle.light`는 오류 없이 버린다. `pickup.accept`·`pickup.reject`·`session.boarded`가 상태에 맞지 않으면 `INVALID_STATE`, 진행 중 세션이 없으면 `SESSION_NOT_FOUND`.
- **상태와 보관**: 실제 enum은 REQUESTED·SHARING뿐이다. 종료 시 세션·두 인덱스·수신 시각을 제거하고 양쪽에 `session.ended`를 보낸다. 종료 상태·이력·마지막 좌표는 남기지 않는다.
- **만료**: 5초 fixed delay 검사에서 생성 후 60분 이상 → REQUESTED 5분 이상 → SHARING의 어느 한쪽 위치 무수신 2분 초과 순서로 판단한다. 위치를 한 번도 안 보냈으면 승인 시각을 기준으로 한다. `ts`·좌표의 나이가 아닌 서버 수신 시각을 쓴다. 소켓 해제만으로 즉시 종료하지 않는다.
- **payload 처리**: `pickup.request.riderName`은 검사하지만 위치 필드·ETA의 타입·범위·source는 검증하지 않는다. 위치는 세 필드를 꺼내 중계하고 ETA는 Map 그대로 중계한다. 실내등은 `on == true`만 true, 그 외는 false로 보낸다. `session.end`의 reason은 읽지 않고 `manual`로 통일한다.
- **연결과 복구**: 핸드셰이크 후 토큰이 틀리면 4401, 같은 principal의 새 연결은 기존 연결을 4409로 닫는다. 연결 직후 `session.state`에는 진행 상태·탑승자 식별 정보만 담고 위치·ETA·실내등·종료 이력을 재생하지 않는다. 오프라인 수신자에게 메시지를 큐잉하지 않는다.
- **페어링 보관**: 코드는 10분·1회용이며 같은 차가 새 코드를 받으면 이전 코드는 무효다. 재시작하면 코드가 사라진다. 토큰은 만료·개별 폐기 기능 없이 서명을 검증한다. `pairing.completed`를 놓친 차에 가족 목록을 다시 내려주는 API는 없다.

## 6. 기존 테스트와 검증 근거

| 테스트 | 확인 내용 |
|---|---|
| `TokenServiceTest` (5개) | 발급·검증, 서명·payload 변조·다른 비밀키·잘못된 형식 처리, ID 접두사와 12자 규칙 |
| `PairingCodeStoreTest` (5개) | 6자리 코드·1회 소비·10분 만료·만료 전 유효·같은 차의 새 코드 무효화·없는 코드 처리 |
| `SessionServiceTest` (17개) | 요청·승인·위치·ETA·실내등 중계, 종료 이유 6가지, 오프라인·중복·이름·상태·type 오류, 비공유 위치 무시, 5분·2분·60분 만료, 연결 직후 상태 |
| `WebSocketFlowTest` (7개, RANDOM_PORT·`StandardWebSocketClient`) | 잘못된 토큰 4401, REST 오류, 등록→페어링→요청→승인→위치·실내등→탑승 종료, 중복 연결 4409, 공유 중 탑승자 소켓 재연결, 차 오프라인, 잘못된 JSON |

- 세션·코드 단위 테스트는 이미 구현된 `MutableClock.advance(Duration)`으로 시간을 전진시킨다. 위 통합 테스트의 탑승자는 테스트 소켓이며 폰 앱 실행을 뜻하지 않는다.
- 서버 근거: `server/build/test-results/test/TEST-*.xml`의 기존 4개 suite, 총 34개. 공유 모듈은 `protocol/build/test-results/test/`의 9개, 차 앱은 `app/build/test-results/testDebugUnitTest/`의 46개다. 모두 11/07 기존 결과로 실패·오류·건너뜀 0을 읽어 확인했다.
- 기존 서버 JUnit 통과는 Docker 이미지 생성·Railway 배포·실제 사용자 발화·P2 폰 UI 검증이나 P3/P4 실제 폰 통합 검증을 대신하지 않는다.

## 7. 배포 준비와 남은 검증 (Railway)

**설정 파일 작성 완료, Docker image build 검증 대기, Railway 배포 미진행**이다. 아래는 아직 수행해야 할 배포 절차이며 운영 중인 서비스를 설명하지 않는다.

- Eventoday와 같은 Railway 계정에 서비스를 하나 추가한다. Root Directory를 `server`로 지정하면 `server/Dockerfile`로 빌드된다.
- 환경변수: `SPRING_PROFILES_ACTIVE=railway`, `MAEJUNG_TOKEN_SECRET`(32바이트 이상 임의 문자열).
- **비밀키를 바꾸면 기존 토큰이 다음 인증·재연결에서 무효가 된다.** 차는 WebSocket 4401에서 토큰·가족 목록을 비우고 재등록한다. 폰의 토큰 삭제·연결 화면 전환은 아직 설계다. 이미 연결된 소켓을 키 변경 즉시 폐기하는 기능은 없다.
- 헬스체크 경로 `/health`.
- **Railway 서비스 생성과 환경변수 입력은 사용자가 직접 한다**(계정 작업). 에이전트는 Dockerfile과 설정 파일까지만 만든다.
- 서버를 재시작하면 진행 중 세션과 미사용 연결 코드가 사라진다. 같은 키·토큰으로 재연결한 차는 `session.state none`으로 정리한다. 페어링 토큰의 유효성과 세션 보존은 별개다.
- 현재 애플리케이션 설정은 HTTP 접근 로그를 활성화하지 않는다. Railway·프록시가 쿼리의 토큰을 기록하는지 여부는 배포 후 확인해야 한다.
- 남은 확인: `docker build -t maejung-server server` 성공 → 실제 Railway 배포 → `/health`·WSS·재연결 확인 → 배포 URL을 사용한 차 앱 연결 검증. 기존 Hub timeout을 이미지 빌드 성공으로 표시하지 않는다.

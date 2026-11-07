# 마중 서버 설계

차와 폰 사이에서 픽업 요청과 위치를 중계하는 서버. 통신 규격은 [PROTOCOL.md](PROTOCOL.md)가 기준이다.

## 1. 결정 사항 (2026-11-06)

- **직접 만든다.** Supabase(BaaS)도 검토했지만, 무료 요금제는 1주일 미사용 시 일시정지되고 위치를 DB에 저장하게 된다. 사용자가 백엔드 개발자라 익숙한 스택이 더 빠르다.
- **스택은 Eventoday 백엔드(`~/IdeaProjects/eventoday-backend`)와 맞춘다.** Kotlin 2.0.21, Spring Boot 3.4.5, Java 21, Gradle 8.12 wrapper, Dockerfile(gradle:8.12-jdk21 빌드 → eclipse-temurin:21-jre 실행), Railway 배포.
- **DB, Redis, Spring Security, JWT, Firebase는 쓰지 않는다.** 세션과 위치는 메모리에만 둔다. 서버가 위치를 저장하지 않는다는 것 자체가 개인정보 설계다.
- **로그인 대신 HMAC 서명 토큰**(PROTOCOL.md 1절). 서버 재시작에도 페어링은 살아 있다.
- **서버는 한 대.** 수평 확장은 1차 범위가 아니다(기획서에는 "Redis Pub/Sub로 확장 가능"이라고만 적는다).

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

- Gradle wrapper는 8.12(Eventoday와 같게): `gradle wrapper --gradle-version 8.12`로 만들거나 Eventoday의 `gradle/wrapper/`를 복사한다.
- Dockerfile은 Eventoday(`~/IdeaProjects/eventoday-backend/Dockerfile`)와 같은 2단계 구성이다. 위의 `jar { enabled = false }`가 있어야 그대로 동작한다.

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

- **동시성**: 세션은 `HashMap<sessionId, PickupSession>`, 인덱스 `vehicleId → sessionId`, `riderId → sessionId`. `SessionService`의 공개 메서드(`onConnected`, `handle`, `sweep`) 전체를 `@Synchronized` 하나로 묶는다. 1차는 서버 한 대·소규모라 단순함을 택했다. 규모가 커지면 세션 단위 락이나 Redis로 바꾼다.
- **WebSocket 송신**: Spring `WebSocketSession.sendMessage`는 스레드 안전하지 않다. `ConcurrentWebSocketSessionDecorator(session, 5000, 64 * 1024)`로 감싸서 등록한다.
- **시간**: `Clock`을 주입받아 쓴다. `System.currentTimeMillis()`를 직접 부르지 않는다(만료 테스트를 위해).
- **로그**: type, sessionId, principal 종류만 남긴다. 좌표·이름은 로그에 남기지 않는다.
- **포트**: `server.port=${PORT:8080}` (Railway가 PORT를 준다).
- **비밀키**: `application.yml`에 `maejung.token-secret: ${MAEJUNG_TOKEN_SECRET:local-dev-secret-do-not-use-in-prod-0123456789}`. 로컬 `bootRun`과 테스트는 이 기본값으로 돈다. 배포용 `application-railway.yml`에는 `maejung.token-secret: ${MAEJUNG_TOKEN_SECRET}`처럼 기본값 없이 둔다. Railway에서는 `SPRING_PROFILES_ACTIVE=railway`(Eventoday와 같은 방식)를 주므로, 환경변수가 빠지면 기동이 실패한다.
- **토큰 페이로드**: PROTOCOL.md 1절. id는 `v_`/`r_`/`s_` 접두사 + 하이픈을 뺀 UUID 앞 12자.
- **탑승자 이름**: 토큰에 없다. `pickup.request` payload의 `riderName`을 세션에 담아 `pickup.requested`·`session.state`에 쓴다.
- **세션 인덱스로 찾기**: 클라이언트 메시지의 `sessionId`는 무시하고 `vehicleId → sessionId`, `riderId → sessionId` 인덱스로 찾는다(PROTOCOL.md 3절).
- **요청 처리 순서**: `pickup.request` → 차 연결이 없으면 `error VEHICLE_OFFLINE`(세션 안 만듦) → 차나 탑승자에 진행 중 세션이 있으면 `error SESSION_ALREADY_ACTIVE` → 세션 생성 → 차에 `pickup.requested`, 폰에 `pickup.pending`.
- **위치 저장**: **좌표는 저장하지 않고 중계만 한다.** 만료 판단에 필요한 차·탑승자 각각의 마지막 위치 수신 시각만 세션에 둔다.
- **SHARING이 아닐 때** 오는 `location.update`·`eta.update`·`vehicle.light`는 오류 없이 버린다. `pickup.accept`·`pickup.reject`·`session.boarded`가 상태에 맞지 않으면 `INVALID_STATE`, 진행 중 세션이 없으면 `SESSION_NOT_FOUND`.

## 6. 테스트 (완료 기준)

| 테스트 | 확인 내용 |
|---|---|
| `TokenServiceTest` | 발급한 토큰 검증 성공, 서명 변조·다른 비밀키·종류 불일치 실패 |
| `PairingCodeStoreTest` | 10분 지나면 만료, 한 번 쓰면 사라짐, 같은 차의 새 코드가 이전 코드를 무효화 |
| `SessionServiceTest` | PROTOCOL.md 4절의 모든 전이, 종료 이유 6가지, `VEHICLE_OFFLINE`·`SESSION_ALREADY_ACTIVE`, SHARING이 아닐 때 `location.update` 무시. 조작 가능한 Clock으로 5분·2분(위치 없을 때 accept 기준 포함)·60분 만료 |
| `WebSocketFlowTest` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, OkHttp 또는 `StandardWebSocketClient`) | ① 틀린 토큰 → close 4401 ② 차·폰 연결 직후 `session.state none` ③ 등록 → 코드 → 페어링 → 요청(`pickup.requested`/`pickup.pending`) → 승인 → 위치 교환 → `session.boarded` → 양쪽 `session.ended(boarded)` ④ 같은 토큰 재연결 시 이전 연결 4409 ⑤ SHARING 중 폰 재연결 → `session.state sharing` |

- 조작 가능한 Clock: 테스트용 `MutableClock`(Clock 상속, `advance(Duration)`)을 직접 만든다.

## 7. 배포 (Railway)

- Eventoday와 같은 Railway 계정에 서비스를 하나 추가한다. Root Directory를 `server`로 지정하면 `server/Dockerfile`로 빌드된다.
- 환경변수: `SPRING_PROFILES_ACTIVE=railway`, `MAEJUNG_TOKEN_SECRET`(32바이트 이상 임의 문자열).
- **비밀키를 바꾸면 기존 토큰이 모두 무효가 된다.** 차·폰 모두 4401을 받아 차는 재등록, 폰은 연결 화면으로 간다. 가족 연결을 처음부터 다시 해야 하니 PT 전에는 절대 바꾸지 않는다.
- 헬스체크 경로 `/health`.
- **Railway 서비스 생성과 환경변수 입력은 사용자가 직접 한다**(계정 작업). 에이전트는 Dockerfile과 설정 파일까지만 만든다.
- PT(12/3~4) 전에는 재배포하지 않는다. 재시작하면 진행 중 세션이 사라진다(페어링은 유지되고, 클라이언트는 `session.state none`을 받아 정리한다).
- 1차에는 HTTP 접근 로그를 켜지 않는다(토큰이 쿼리 문자열에 있다).

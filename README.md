# 마중

현대자동차그룹 차량용 앱 개발 공모전(Pleos App Market) 출품작. 탑승할 사람이 폰에서 "데리러 와줘"를 누르면 차에 알림이 뜨고, 운전자가 승인하면 서로의 위치가 공유됩니다. 가까워지면 실내등을 켜서 차를 찾게 하고, 타면 차가 탑승을 알아채 공유를 스스로 끝냅니다.

> 이 README는 작성 중입니다. 차 앱·폰 앱 실행 방법, 테스트 환경, APK 위치는 1차 제출 전에 채웁니다.

## 구성

| 폴더 | 내용 |
|---|---|
| `app/` | 차 앱 (Pleos Connect, Android Automotive) |
| `server/` | 중계 서버 (Kotlin, Spring Boot, WebSocket) |
| `docs/` | 설계 문서 |

## 서버

### 로컬 실행

JDK 21이 필요합니다.

```bash
cd server
./gradlew test       # 단위·통합 테스트
./gradlew bootRun    # http://localhost:8080
curl localhost:8080/health
```

에뮬레이터에서는 `http://10.0.2.2:8080`으로 접속합니다.

### Railway 배포

1. Railway 프로젝트에 서비스를 추가하고 이 저장소를 연결합니다.
2. **Root Directory**를 `server`로 지정합니다. `server/Dockerfile`로 빌드됩니다.
3. 환경변수를 넣습니다.
   - `SPRING_PROFILES_ACTIVE=railway`
   - `MAEJUNG_TOKEN_SECRET`: 32자 이상 임의 문자열 (예: `openssl rand -base64 48`). 바꾸면 모든 차·폰 연결이 무효가 됩니다.
4. 헬스체크 경로를 `/health`로 지정합니다.
5. 배포 URL을 차·폰 앱 release 빌드의 `maejung.serverUrl`에 넣습니다.

통신 규격은 [docs/PROTOCOL.md](docs/PROTOCOL.md), 서버 설계는 [docs/SERVER.md](docs/SERVER.md)에 있습니다.

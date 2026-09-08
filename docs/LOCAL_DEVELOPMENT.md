# Local Development Guide (로컬 개발 가이드)

로컬 환경에서 GoldPet 프로젝트를 개발하고 테스트하는 방법을 안내합니다.

## 환경 구성

| 환경 | 도메인 | 용도 |
|-----|--------|------|
| **local** | localhost | 로컬 개발 |
| **dev** | *.mannamsquare.com | 개발 서버 |
| **prod** | *.goldpet.com | 상용 서버 |

## 1. 사전 준비

- **Docker Desktop**: DB, Redis, MinIO 실행
- **Java 21 (JDK)**: 백엔드
- **Node.js (v20+)**: 프론트엔드/Admin
- **Flutter SDK**: 모바일 앱

## 2. 인프라 실행 (Docker)

```bash
cd deploy-local
docker compose up -d
```

### 로컬 포트 매핑

| 서비스 | 포트 | 설명 |
|-------|------|------|
| PostgreSQL | **5433** | PostGIS 포함 |
| Redis | 6379 | 캐시/세션 |
| MinIO API | **9100** | S3 호환 스토리지 |
| MinIO Console | **9101** | 웹 콘솔 |

## 3. 백엔드 (API) 실행

```bash
cd api
./gradlew bootRun
# http://localhost:8081
```

환경 프로필: `SPRING_PROFILES_ACTIVE=local` (기본값)

## 4. 프론트엔드 (React) 실행

```bash
cd frontend
npm install
npm run dev
# http://localhost:5173
```

`.env.local` 파일이 자동으로 로드됩니다.

## 5. Admin 대시보드 실행

```bash
cd admin
npm install
npm run dev
# http://localhost:5174
```

## 6. 모바일 앱 (Flutter) 실행

```bash
cd app
flutter pub get
flutter run --dart-define=ENV=local
```

## 7. 환경별 빌드

### Frontend/Admin

```bash
npm run build -- --mode dev   # 개발 서버용
npm run build -- --mode prod  # 상용 서버용
```

### Flutter

```bash
flutter run --dart-define=ENV=dev   # 개발 서버
flutter run --dart-define=ENV=prod  # 상용 서버
```

---

## FAQ

### Q: DB 데이터 초기화

```bash
cd deploy-local
docker compose down -v
docker compose up -d
```

### Q: 포트 충돌

로컬에 PostgreSQL이 이미 있다면 `deploy-local/docker-compose.yml`에서 포트 5433을 다른 값으로 변경하세요.

### Q: MinIO 콘솔 접속

브라우저에서 http://localhost:9101 접속
- ID: `minioadmin`
- PW: `minioadmin`

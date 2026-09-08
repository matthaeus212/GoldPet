# GoldPet 로컬 개발환경 설정 가이드

이 문서는 GoldPet 모노레포(`app/`, `frontend/`, `admin/`, `api/`)를 로컬 머신에서 실행하기 위한 설정을 정리한다. 프로젝트 개요·명령어는 [CLAUDE.md](./CLAUDE.md), 더 짧은 요약은 [docs/LOCAL_DEVELOPMENT.md](./docs/LOCAL_DEVELOPMENT.md) 참고.

## 1. 사전 준비 — 설치 순서

macOS(Homebrew) 기준. **의존 관계가 있는 순서대로** 설치한다 — 뒤 단계는 앞 단계가 끝나야 검증 가능하다.

| # | 설치 항목 | 용도 | 설치 명령 |
|---|----------|------|----------|
| 1 | Homebrew | 나머지 도구 설치용 패키지 매니저 | (이미 있음 없으면) `/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"` |
| 2 | Git | 소스 관리 | macOS 기본 내장 (Xcode CLT 포함) |
| 3 | Docker Desktop | PostgreSQL(PostGIS)/Redis/MinIO 컨테이너 실행 — §2 | `brew install --cask docker` (설치 후 Docker.app 최초 1회 실행 필요) |
| 4 | JDK 21 | Spring Boot API 빌드/실행 — §3 (Gradle 8.8, `sourceCompatibility = 21`) | `brew install --cask temurin@21` 또는 `brew install --cask corretto@21` |
| 5 | Node.js 20+ | frontend/admin (Vite + React 19) — §4, §5 | `brew install nvm && nvm install 20 && nvm use 20` (또는 `brew install node@20`) |
| 6 | Flutter SDK (Dart `^3.9.2` 내장) | 모바일 앱 — §6 | `brew install --cask flutter` |
| 7 | Xcode + CocoaPods | iOS 빌드 (`app/ios/Podfile` 존재) | App Store에서 Xcode 설치 → `sudo xcode-select -s /Applications/Xcode.app` → `brew install cocoapods` |
| 8 | Android Studio (+ Android SDK/에뮬레이터) | Android 빌드 | `brew install --cask android-studio` 후 최초 실행 시 SDK Manager로 SDK 설치 |
| 9 | gitleaks (선택) | 커밋 전 시크릿 스캔 pre-commit 훅 활성화 (`frontend/.husky/pre-commit`) — 미설치 시 훅이 경고만 하고 스킵 | `brew install gitleaks` |
| 10 | Playwright 브라우저 (선택) | `npm run test:e2e` (frontend/admin) 실행 시 | frontend·admin `npm install` 후 `npx playwright install` |

설치 후 검증:
```bash
docker --version && java --version && node --version && flutter --version && git --version
```

> **이 문서를 준비하며 확인한 현재 머신 기준 특이사항**(참고용, 머신마다 다를 수 있음):
> - JDK 21이 이미 설치돼 있어도(`/usr/libexec/java_home -V`로 확인) 셸의 `JAVA_HOME`이 17로 고정돼 있으면 `./gradlew bootRun`이 17로 빌드를 시도해 `sourceCompatibility=21` 요구사항과 어긋난다. `~/.zshrc`에 `export JAVA_HOME=$JAVA_HOME17` 같은 줄이 있는지 확인하고, API 작업 시에는 21을 가리키도록 바꾸거나 `jenv`/direnv로 프로젝트별 버전을 분리한다.
> - `flutter`, `gitleaks`는 기본 macOS/Homebrew 셋업에 포함되지 않으므로 §6(Flutter 앱)과 커밋 훅을 쓰려면 위 표의 7·9번을 별도로 설치해야 한다.

## 2. 인프라 (Docker: DB / Redis / MinIO)

```bash
./deploy-local/scripts/start.sh   # 시작
./deploy-local/scripts/stop.sh    # 중지
./deploy-local/scripts/reset.sh   # 볼륨 삭제 후 재시작 (DB 초기화)
```

내부적으로 `deploy-local/docker-compose.yml` + `deploy-local/.env`를 사용한다. `.env`는 저장소에 커밋되어 있음(로컬 전용 고정 비밀번호이므로 실제 비밀 유출 위험 없음):

| 서비스 | 포트 | 접속 정보 |
|-------|------|----------|
| PostgreSQL(PostGIS 16) | `localhost:5433` | `goldpet` / `goldpet123`, DB명 `goldpet` |
| Redis 7 | `localhost:6379` | password `goldpet123` |
| MinIO API (S3 호환) | `localhost:9100` | access `goldpet-s3-access-key` / secret `goldpet-s3-secret-key` |
| MinIO Console | http://localhost:9101 | 위와 동일 계정 |

> `docs/LOCAL_DEVELOPMENT.md` FAQ의 MinIO 계정(`minioadmin`/`minioadmin`)은 **오래된 값**이라 실제와 다르다. 실제 계정은 `deploy-local/.env`의 `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`를 따른다.

PostgreSQL 로컬 포트가 이미 사용 중이면 `deploy-local/docker-compose.yml`의 `5433:5432` 매핑만 바꾸면 된다(API의 `DB_PORT` 환경변수도 함께 변경).

S3 버킷(`goldpet-public`, `goldpet-private`)은 수동 생성이 필요 없다 — API가 첫 업로드 시 `FileService.ensureBucket()`에서 자동 생성하고 public 버킷엔 공개 읽기 정책을 붙인다.

## 3. 백엔드 API (Spring Boot / Kotlin)

```bash
cd api
./gradlew bootRun     # http://localhost:8081
./gradlew test
```

- 기본 프로필: `SPRING_PROFILES_ACTIVE=local` (`application.yml`의 기본값, 별도 설정 불필요)
- 설정 우선순위: `application.yml`(공통, 환경변수로 오버라이드 가능) → `application-local.yml`(local 프로필 전용, 값이 하드코딩되어 우선 적용됨)
- DB 연결: `jdbc:postgresql://localhost:5433/goldpet` (환경변수 `DB_HOST`/`DB_PORT`/`DB_USER`/`DB_PASSWORD`/`DB_NAME`로 오버라이드 가능)
- Flyway 마이그레이션이 기동 시 자동 적용됨 (`api/src/main/resources/db/migration/V*.sql`), `baseline-on-migrate: true`
- OAuth2(Google/Naver/Kakao)는 `application.yml`에 로컬 개발용 client-id/secret이 이미 박혀 있어 추가 설정 없이 동작. Apple 로그인은 local에서 비활성(주석 처리).
- `app/encryption/key`, `JWT_SECRET` 등도 local 프로필 기본값이 코드에 포함되어 있어 별도 `.env` 파일 없이 바로 실행 가능.
- CORS 허용 origin: `http://localhost:5173`, `http://localhost:5174`, `http://10.0.2.2:5173`(Android 에뮬레이터), `http://192.168.0.87:5173`(LAN, §5 참고)

### MinIO(S3) 연동 — 이중 엔드포인트

`application-local.yml`은 S3 엔드포인트를 두 개로 분리한다:
- `S3_ENDPOINT=http://localhost:9100` — API 서버가 직접 MinIO에 접속할 때 사용
- `S3_PUBLIC_ENDPOINT=http://192.168.0.87:9100` — 업로드된 이미지 URL에 박히는 주소로, **실기기(Android)나 Flutter 앱에서 접근 가능해야 함**

`192.168.0.87`은 원 개발자의 LAN IP이므로, 실기기 테스트가 필요하면 본인 머신의 LAN IP로 바꿔야 한다(§5 참고).

## 4. Frontend (React 19 / Vite)

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
npm run build -- --mode dev    # 개발 서버 빌드
npm run build -- --mode prod   # 상용 서버 빌드
npm run lint
```

`.env.local`은 `.gitignore`에 등록되어 있고 **저장소에 파일 자체가 없다** — 최초 실행 전 직접 생성해야 한다:

```bash
cd frontend
cp .env.example .env.local
```

`.env.local` 권장 값:
```
VITE_API_BASE_URL=http://localhost:8081
VITE_ENV=local
VITE_APP_URL=http://localhost:5173
VITE_GA_ID=            # 비워두면 애널리틱스 no-op
```

## 5. Admin 대시보드

```bash
cd admin
npm install
npm run dev      # http://localhost:5174
npm run build
npm run lint
```

`admin/.env.local`은 이미 저장소에 커밋되어 있어 별도 생성 불필요:
```
VITE_API_BASE_URL=http://localhost:8081
VITE_ENV=local
```

## 6. Flutter 앱

```bash
cd app
flutter pub get
flutter run --dart-define=ENV=local
```

`app/lib/core/config/environment.dart`의 `EnvironmentConfig.local`이 `apiBaseUrl`/`appBaseUrl`/`adminBaseUrl`/`s3BaseUrl`을 **하드코딩된 LAN IP `192.168.0.87`** 로 가리킨다(에뮬레이터/실기기가 `localhost`로 개발 PC에 접근할 수 없기 때문). 다른 네트워크에서 개발한다면:

1. `ipconfig getifaddr en0` (macOS)로 본인 PC의 LAN IP 확인
2. `app/lib/core/config/environment.dart`의 `local` 설정 IP 교체
3. `api/src/main/resources/application-local.yml`의 `S3_PUBLIC_ENDPOINT`, `cors.allowed-origins`도 동일 IP로 교체

> `dart-define=ENV`를 생략하면 기본값이 `dev`(원격 개발 서버)로 잡히므로, 로컬 API를 보려면 반드시 `ENV=local`을 명시해야 한다.

## 7. 전체 기동 순서 (처음 세팅 시)

```bash
# 1) 인프라
./deploy-local/scripts/start.sh

# 2) API
cd api && ./gradlew bootRun &

# 3) Frontend
cd frontend && cp .env.example .env.local && npm install && npm run dev &

# 4) Admin
cd admin && npm install && npm run dev &

# 5) (선택) Flutter
cd app && flutter pub get && flutter run --dart-define=ENV=local
```

## 8. 트러블슈팅

| 증상 | 원인 / 조치 |
|-----|-----------|
| API가 DB에 못 붙음 | `docker compose ps`로 `goldpet-db-local` 기동 확인, 포트 5433 충돌 여부 확인 |
| 이미지 업로드 후 404 | `S3_PUBLIC_ENDPOINT`가 접근 불가능한 IP로 설정된 경우 (§3, §6) |
| Android 에뮬레이터가 API 접근 불가 | 에뮬레이터는 `localhost` 대신 `10.0.2.2` 사용 (CORS엔 이미 허용됨), 실기기는 LAN IP 필요 |
| DB 스키마 꼬임 | `./deploy-local/scripts/reset.sh`로 볼륨 삭제 후 Flyway 재적용 |
| frontend `.env.local` 없음 오류 | §4 참고, `.env.example` 복사 필요 (admin은 이미 커밋되어 있어 해당 없음) |

## 9. 참고 문서

- [CLAUDE.md](./CLAUDE.md) — 프로젝트 전체 개요, 3환경(local/dev/prod) 구조, 배포
- [docs/LOCAL_DEVELOPMENT.md](./docs/LOCAL_DEVELOPMENT.md) — 짧은 버전 가이드 (일부 MinIO 계정 정보 오래됨, 본 문서 §2 참고)
- [deploy-local/README.md](./deploy-local/README.md) — Docker 인프라 상세

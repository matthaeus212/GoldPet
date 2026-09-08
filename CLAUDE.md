# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GoldPet is a pet social networking platform with hybrid mobile architecture. The app enables walk tracking, friend matching, chat, AI profile generation, and community features for pet owners.

## Repository Structure

This is a monorepo with four main projects:

- **app/** - Flutter hybrid mobile app (native features + React WebView)
- **frontend/** - React 19 web app for users (Vite, TypeScript, Tailwind)
- **admin/** - React admin dashboard (Vite, TypeScript, Tailwind)
- **api/** - Spring Boot 3.2 backend (Kotlin, Java 21)
- **deploy-local/** - 로컬 개발용 Docker 설정
- **deploy-dev/** - 개발 서버 배포 설정 (mannamsquare.com)

## Development Commands

### Infrastructure (Docker services)
```bash
# Start local services (PostgreSQL+PostGIS, Redis, MinIO)
cd deploy-local && docker compose up -d
```

### Backend API (Spring Boot)
```bash
cd api
./gradlew bootRun                    # Run development server
./gradlew test                       # Run tests
./gradlew build                      # Build JAR
```

### Frontend (React web app)
```bash
cd frontend
npm install
npm run dev      # Dev server on port 5173
npm run build    # Production build
npm run lint     # ESLint
```

### Admin Dashboard
```bash
cd admin
npm install
npm run dev      # Dev server on port 5174
npm run build
npm run lint
```

### Flutter App
```bash
cd app
flutter pub get
flutter run                          # Run on connected device/emulator
dart run build_runner build          # Generate code (Freezed, Riverpod)
flutter build apk                    # Android release
flutter build ios                    # iOS release
```

> **릴리스 빌드(Play Console/App Store):** Android는 반드시 `flutter build appbundle --build-number=N` 사용 (`N` = `pubspec.yaml` 빌드번호). `flutter build` 경로가 `android/local.properties`의 `flutter.versionCode`를 pubspec 기준으로 재생성한다. `cd app/android && ./gradlew bundleRelease` 같은 직접 Gradle 경로는 stale한 `local.properties`(gitignore·로컬 전용)의 versionCode로 패키징될 수 있어 Play Console 업로드 거부 위험 — 직접 Gradle 호출 금지.

## Architecture

### Hybrid App Pattern
Flutter handles native capabilities (GPS, camera, push notifications, permissions) while React handles all UI and business logic via WebView. Communication happens through a JavaScript bridge:

- **Flutter → React**: Inject JavaScript and execute functions
- **React → Flutter**: `nativeBridge.callMethod('methodName', params)`

Key bridge methods: `getCurrentLocation`, `openCamera`, `startLocationTracking`

### Backend Domain Structure
The API uses domain-driven design with modules under `api/src/main/kotlin/com/goldpet/domain/`:
- auth, user, pet, walk, friend, chat, community, gold, notification, gamification, checkin, file, report, admin, aiprofile

### State Management
- **Flutter**: Riverpod with Freezed for immutable models
- **React**: Zustand stores + React Query for server state + Context API

### Database
PostgreSQL 16 with PostGIS extension for geospatial features:
- `geometry(Point, 4326)` for user/place locations
- `geometry(LineString, 4326)` for walk routes
- Flyway migrations in `api/src/main/resources/`

## Key Technical Details

- **Maps**: Naver Maps API (flutter_naver_map for native, web API for React)
- **Auth**: OAuth2 with Kakao, Naver, Google, Apple; JWT tokens
- **Storage**: AWS S3 (production), MinIO (local dev)
- **Real-time**: WebSocket for chat features
- **Font**: Pretendard (Korean typeface) defined in app/pubspec.yaml

## Figma Integration

When implementing designs from Figma:
1. Use `get_design_context` first to fetch structured representation
2. Use `get_screenshot` for visual reference
3. Translate output to project conventions - reuse existing components
4. Match project's color tokens, typography, and spacing
5. Validate for 1:1 visual parity with design

## Environment

### 3가지 환경 구성

| 환경 | 도메인 패턴 | 용도 |
|-----|------------|------|
| **local** | localhost | 로컬 개발 |
| **dev** | {admin,api,app}.mannamsquare.com | 개발/테스트 서버 |
| **prod** | {admin,api,app}.goldpet.com | 상용 서버 |

### 환경별 URL 매핑

| 서비스 | local | dev | prod |
|-------|-------|-----|------|
| API | http://localhost:8081 | https://api.mannamsquare.com | https://api.goldpet.com |
| Frontend | http://localhost:5173 | https://app.mannamsquare.com | https://app.goldpet.com |
| Admin | http://localhost:5174 | https://admin.mannamsquare.com | https://admin.goldpet.com |

### 환경 설정 파일

**Frontend/Admin (Vite)**
- `.env.local` - 로컬 개발 (npm run dev)
- `.env.dev` - 개발 서버 빌드 (`npm run build -- --mode dev`)
- `.env.prod` - 상용 서버 빌드 (`npm run build -- --mode prod`)

**API (Spring Boot)**
- `application-local.yml` - SPRING_PROFILES_ACTIVE=local
- `application-dev.yml` - SPRING_PROFILES_ACTIVE=dev
- `application-prod.yml` - SPRING_PROFILES_ACTIVE=prod

**Flutter App**
```bash
flutter run --dart-define=ENV=local    # 로컬 개발
flutter run --dart-define=ENV=dev      # 개발 서버
flutter run --dart-define=ENV=prod     # 상용 서버
```

### Jenkins 배포

| 프로젝트 | 파라미터 | dev | prod |
|---------|---------|-----|------|
| API | PROFILE | dev | prod |
| Frontend | ENV | dev | prod |
| Admin | ENV | dev | prod |

### 로컬 개발 환경

```bash
# 인프라 시작/중지/리셋
./deploy-local/scripts/start.sh
./deploy-local/scripts/stop.sh
./deploy-local/scripts/reset.sh   # 데이터 초기화 후 재시작
```

로컬 서비스 포트:
- PostgreSQL: localhost:5433 (goldpet/goldpet123)
- Redis: localhost:6379 (password: goldpet123)
- MinIO: localhost:9100 (API), localhost:9101 (Console)
- API: localhost:8081
- Frontend: localhost:5173
- Admin: localhost:5174

### 개발 서버 배포

```bash
# 개발 서버 배포 스크립트 (mannamsquare.com)
./deploy-dev/scripts/deploy-api.sh
./deploy-dev/scripts/deploy-frontend.sh
./deploy-dev/scripts/deploy-admin.sh
```

## 폴더 구조

```
deploy-local/                    # 로컬 개발 환경
├── docker-compose.yml
├── config/postgres/
└── scripts/
    ├── start.sh
    ├── stop.sh
    └── reset.sh

deploy-dev/                      # 개발 서버 (mannamsquare.com)
├── docker/
│   ├── docker-compose.yml
│   └── install-docker.sh
├── nginx/
│   ├── nginx.conf
│   └── sites-available/
├── systemd/services/
├── logrotate/
└── scripts/
    ├── deploy-api.sh
    ├── deploy-frontend.sh
    └── deploy-admin.sh
```

## 하네스: 종합 코드 리뷰

**목표:** 변경(diff/PR/경로)을 아키텍처·보안·성능·코드스타일 4개 차원으로 병렬 감사하고, 외부 독립 AI(codex/gemini) 리뷰를 더해 하나의 리포트로 통합한다.

**트리거:** 코드 리뷰·종합 리뷰·아키텍처/보안/성능/스타일 점검·PR 리뷰·변경 감사 요청 시 `code-review-team` 스킬을 사용하라. 기본 대상은 현재 브랜치 diff(vs main), 인자로 경로/PR/커밋범위 override. 단순 질문은 직접 응답 가능.

**듀얼 런타임:** Codex는 레포 루트 `AGENTS.md` 포인터 + `.codex/agents/*.toml` + `.agents/skills/`(→`.claude/skills` 심링크) 사용. 한쪽만 갱신하면 drift — 수정 시 양쪽 동기화.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-06-19 | 초기 구성 (팬아웃/팬인 팀 5에이전트 + 외부리뷰 + 종합) | 전체 | - |
| 2026-06-19 | 외부리뷰 실행기 번들 스크립트화 + 우아한 저하 | skills/external-review (run-external-review.sh) | 실행 중 gemini free-tier 미지원(IneligibleTierError) + timeout 깨진 심링크 실패 → timeout 실행검증·도구 헬스체크·codex 단독 저하 모드 내장 |

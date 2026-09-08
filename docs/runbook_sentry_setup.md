# Runbook: Sentry SDK 4 프로젝트 통합

> 작성일: 2026-05-26
> 대상 Sprint: 0 (DSN 발급) + Sprint 1 (SDK 통합)
> 책임자: Ops Lead (DSN 발급) + BE/FE/Admin/Flutter Lead (SDK 통합)
> 관련 BLOCKER: §1-1 #9 (Sentry SDK 4 프로젝트 전무)

---

## Sprint 0 단계 — DSN 발급 (Ops 작업)

### 1. Sentry 조직 생성 (또는 기존 조직 사용)
- URL: https://sentry.io/signup/
- Organization slug: `mannamsquare` (또는 `goldpet`)
- 회사 도메인 메일 (`admin@mannamsquare.com`)로 가입.
- 2FA 필수 활성화 + 백업 코드 1Password Business 저장.

### 2. 4 프로젝트 생성

| 프로젝트 슬러그 | Platform | DSN 환경 변수 | 비고 |
|---|---|---|---|
| `goldpet-api` | Java (Spring Boot) | `SENTRY_DSN_API` | dev + prod 분리 |
| `goldpet-frontend` | React | `VITE_SENTRY_DSN_FRONTEND` | dev + prod 분리 |
| `goldpet-admin` | React | `VITE_SENTRY_DSN_ADMIN` | dev + prod 분리 |
| `goldpet-flutter` | Flutter | `SENTRY_DSN_FLUTTER` | dev + prod 분리 |

각 프로젝트마다 **dev DSN / prod DSN 2개 발급** (총 8개 DSN).

### 3. 환경변수 매트릭스 (.env 템플릿)

#### `frontend/.env.dev` 추가
```
VITE_SENTRY_DSN_FRONTEND=https://xxxx@xxxx.ingest.sentry.io/xxxx
VITE_SENTRY_ENV=dev
VITE_SENTRY_TRACES_SAMPLE_RATE=0.1
```

#### `frontend/.env.prod` 추가
```
VITE_SENTRY_DSN_FRONTEND=https://yyyy@yyyy.ingest.sentry.io/yyyy
VITE_SENTRY_ENV=prod
VITE_SENTRY_TRACES_SAMPLE_RATE=0.05
```

#### `admin/.env.dev` / `admin/.env.prod` — 동일 패턴 (`VITE_SENTRY_DSN_ADMIN`).

#### `api/src/main/resources/application-dev.yml` 추가
```yaml
sentry:
  dsn: ${SENTRY_DSN_API}
  environment: dev
  traces-sample-rate: 0.1
  release: ${SENTRY_RELEASE:dev-local}
```

#### `api/src/main/resources/application-prod.yml` 추가
```yaml
sentry:
  dsn: ${SENTRY_DSN_API}
  environment: prod
  traces-sample-rate: 0.05
  release: ${SENTRY_RELEASE}
```

#### Flutter — `--dart-define` 또는 `firebase_remote_config`로 주입
```bash
flutter build apk --dart-define=ENV=prod --dart-define=SENTRY_DSN=https://zzzz...
```

### 4. Jenkins 시크릿 등록
- Jenkins → Credentials → Add Secret Text:
  - `SENTRY_DSN_API_DEV`, `SENTRY_DSN_API_PROD`
  - `SENTRY_DSN_FRONTEND_DEV`, `SENTRY_DSN_FRONTEND_PROD`
  - `SENTRY_DSN_ADMIN_DEV`, `SENTRY_DSN_ADMIN_PROD`
  - `SENTRY_DSN_FLUTTER_DEV`, `SENTRY_DSN_FLUTTER_PROD`
  - `SENTRY_AUTH_TOKEN` (source map upload용)

### 5. Sentry Alert Rule 사전 셋업

| 알림명 | 조건 | 채널 |
|---|---|---|
| 5xx Spike | 5xx error rate >5% 5min 연속 | Slack #ops-alert |
| 결제 실패 | `GoldTransactionException` 이벤트 1건 발생 | Slack #payment-alert |
| FCM 실패율 | `FcmException` 5% over 10min | Slack #ops-alert |
| Crash-free <99% | session crash rate >1% 1h | Slack #ops-alert |

---

## Sprint 1 단계 — SDK 통합 (4 프로젝트 병렬)

### 1. Backend (`api/`) — Spring Boot

#### `api/build.gradle.kts` 추가
```kotlin
dependencies {
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:7.18.0")
    implementation("io.sentry:sentry-logback:7.18.0")
}
```

#### 초기화 자동 (Spring Boot Starter)
- `application-*.yml`의 `sentry.dsn`만 설정하면 자동 초기화.
- MDC `requestId/userId` 자동 captures (Spring Boot Starter integration).

#### 검증
```bash
cd api && ./gradlew bootRun
curl http://localhost:8081/api/test/sentry  # 의도적 throw → Sentry 콘솔에서 이벤트 확인
```

---

### 2. Frontend (`frontend/`) — React

#### `frontend/package.json` 추가
```bash
npm install --save @sentry/react @sentry/vite-plugin
```

#### `frontend/src/main.tsx` 초기화 추가
```typescript
import * as Sentry from '@sentry/react';

if (import.meta.env.VITE_SENTRY_DSN_FRONTEND) {
  Sentry.init({
    dsn: import.meta.env.VITE_SENTRY_DSN_FRONTEND,
    environment: import.meta.env.VITE_SENTRY_ENV,
    tracesSampleRate: parseFloat(import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE ?? '0.1'),
    release: import.meta.env.VITE_APP_VERSION,
    integrations: [Sentry.browserTracingIntegration()],
  });
}
```

#### `frontend/vite.config.ts` — source map upload 플러그인 추가
```typescript
import { sentryVitePlugin } from '@sentry/vite-plugin';

export default defineConfig({
  plugins: [
    react(),
    sentryVitePlugin({
      org: 'mannamsquare',
      project: 'goldpet-frontend',
      authToken: process.env.SENTRY_AUTH_TOKEN,
    }),
  ],
  build: { sourcemap: true },
});
```

---

### 3. Admin (`admin/`) — React (frontend와 동일 패턴)

`@sentry/react` + `@sentry/vite-plugin` 동일하게 통합. DSN만 `VITE_SENTRY_DSN_ADMIN` 사용.

---

### 4. Flutter (`app/`)

#### `app/pubspec.yaml` 추가
```yaml
dependencies:
  sentry_flutter: ^8.11.0
```

#### `app/lib/main.dart` 초기화
```dart
import 'package:sentry_flutter/sentry_flutter.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  const sentryDsn = String.fromEnvironment('SENTRY_DSN');
  if (sentryDsn.isNotEmpty) {
    await SentryFlutter.init((options) {
      options.dsn = sentryDsn;
      options.environment = const String.fromEnvironment('ENV', defaultValue: 'dev');
      options.tracesSampleRate = 0.1;
    }, appRunner: () => runApp(const App()));
  } else {
    runApp(const App());
  }
}
```

---

## Acceptance Criteria (Sprint 1 게이트)

| 검증 항목 | 통과 기준 |
|---|---|
| 4 프로젝트 모두 init confirmed event | Sentry 콘솔에서 init event 1건 이상 수신 |
| release tag 자동 부여 | Sentry Releases 탭에 4 프로젝트 release 등록 |
| source map upload (FE/Admin) | Sentry 콘솔 stack trace에 minify 해제된 라인 표시 |
| Flutter dSYM upload (iOS) | Sentry 콘솔 stack trace에 Dart 라인 표시 |
| alert rule 동작 | 의도적 throw → Slack 알림 도달 |
| dev DSN 노이즈 sampling | dev 환경 traces_sample_rate 0.1 이하 적용 |

---

## 운영 가이드 (Sprint 1 이후)

- **alert escalation**: critical → P0 (즉시 hot-fix), high → P1 (당일 처리), medium → 다음 sprint.
- **release tagging**: Jenkins 빌드 시 `SENTRY_RELEASE=v$(git describe --tags)` 자동 주입.
- **PII filtering**: Sentry SDK `beforeSend` hook에서 email/phone/token 마스킹 (Backend는 `SentryOptions.beforeSend`, FE는 `Sentry.init({ beforeSend })`).

---

## 변경 이력
- 2026-05-26: 최초 작성 (Sprint 0 + Sprint 1 통합 runbook).

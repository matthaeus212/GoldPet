# GoldPet 상용 출시 사전 평가 보고서 (v2.1 patched — APPROVED, pending user approval)

> 작성일: 2026-05-26 · v2.1 patch: 2026-05-26 · v2.2 마이그레이션 시프트: 2026-05-26 · 작성: Planner (RALPLAN-DR consensus)
> v1 평가 → Architect 5건 + Critic 8건 directive 반영 → v2 → Architect 재리뷰 5건 patch → v2.1 (Critic APPROVE).
> Patch 항목: ①Sentry SDK BLOCKER 승격 ②BCP runbook BLOCKER 추가 ③Pre-mortem 완화 인프라 명시 ④status 컬럼 ⑤grace window ⑥거버넌스 조항.
>
> **⚠️ 마이그레이션 번호 시프트 (v2.2, 2026-05-26)**: 원안 V63-V68은 V63/V64가 이미 사용 중 (`V63__Place_Version_Column.sql`, `V64__File_Attachments_WebP_Variants.sql`)이라 V65부터 재배정. 본문 SQL 스케치의 `V63/V64/V65` 헤더는 실제 적용 시 아래 매핑으로 읽으십시오:
>
> | 본문 표기 | 실제 적용 | 내용 |
> |---|---|---|
> | V63 | **V65** | gold_idempotency_keys |
> | V64 | **V66** | user_refresh_tokens |
> | V65 | **V67** | oauth_nonces |
> | V66 | **V68** | chat_reports |
> | V67 | **V69** | admin_totp_secrets |
> | V68 | **V70** | admin_audit_logs |

---

## 평가 전제

### 결제 시나리오 분기 (필수 선결)

본 평가는 다음 두 시나리오를 **분리** 평가합니다. 결제 모드 결정이 BLOCKER 우선순위를 좌우합니다.

| 시나리오 | 골드 충전 | Idempotency 필요성 | 권장 출시 시점 |
|---|---|---|---|
| **베타 출시 = IAP OFF** | 충전 비활성, in-app reward(산책 보상)/spend(차감)만 | `@Version` optimistic lock으로 충분 | 즉시(2주 내) — 옵션 A 권장 |
| **정식 출시 = IAP ON** | Apple StoreKit / Google Billing / PG 연동 | **BLOCKER** — DB 기반 `Idempotency-Key` 필수 | 정식 결제 인프라 완료 후 (옵션 C) |

> **핵심 결정**: 베타 시나리오에서 골드 충전(CHARGE)이 IAP/PG 의존이라면 Idempotency가 BLOCKER, in-app reward/spend만이라면 `@Version` optimistic lock으로 충분합니다.

### 본 문서의 범위

- §1, §2, §4, §5: **출시 게이트(Go/No-Go)** 판정 근거.
- §3: **출시 게이트와 무관한 retention/성장 백로그**. 상세 실행 계획은 별도 `docs/product_roadmap_post_launch.md`로 이관 권장.

---

## RALPLAN-DR 합의 헤더

### 원칙 (5)

1. **출시 게이트 ≠ 완벽 게이트**: BLOCKER만 차단, 그 외는 hot-fix 또는 1-2주차 패치로 위임.
2. **증거 우선**: 모든 BLOCKER는 `file:line` 인용 의무. 증거 없는 항목은 자동 [POST-LAUNCH] 강등.
3. **결제 분기 우선**: IAP 활성화 여부가 전체 BLOCKER 우선순위를 결정 → 최소 fallback(베타)으로 안전한 출시 보장.
4. **데이터 정합성 > 기능 풍부도**: 골드/산책/인증은 race condition 0건이 목표, 신규 기능은 후속 sprint.
5. **운영 가능성 > 신기능**: Observability(Sentry, Grafana) + 모더레이션 + Admin 보안 없으면 출시 불가.

### 결정 동인 (3)

1. **사용자 안전**: PII 노출(EXIF GPS, Admin 침해) + UGC 모더레이션 부재 시 앱스토어 리젝/법적 리스크.
2. **금전 정합성**: 골드는 가상화폐 — 중복 적립/차감 1건이라도 발생 시 신뢰도 회복 불가.
3. **출시 일정**: 회사 계정 전환(만남스퀘어 법인) 완료 후 14일 closed test 통과 시 정식 제출.

### 검토 옵션 매트릭스 (A/B/C)

| 옵션 | 설명 | 장점 | 단점 | 권장도 |
|---|---|---|---|---|
| **A: 베타 우선 출시 (IAP OFF)** | 결제 OFF + 4 재설계 항목 + 신규 BLOCKER 3건 + 모더레이션 완료 후 출시 | 출시 일정 단축(2-3주), BLOCKER 범위 최소화, optimistic lock으로 충분 | 매출 발생 지연, 충전 UX 후속 추가 부담 | **★ 강력 권장** |
| **B: Plan-B Fallback** | 옵션 A로 출시 후 1-2개월 내 IAP 활성화 + Idempotency 인프라 추가 | 옵션 A 출시 후 점진적 결제 도입, 위험 분산 | 결제 도입 시 마이그레이션 한번 더 필요 | **fallback 채택** |
| **C: 정식 출시 (IAP ON 즉시)** | Idempotency + IAP + 모든 BLOCKER + 모더레이션 완료 후 정식 제출 | 한번에 매출 가능 | 출시 지연 4-6주, BLOCKER 표면적 확대 | 비권장(트리거: 투자/제휴 요구 시) |

**권장**: **옵션 A** 베타 우선 출시 → 14일 closed test 통과 → 옵션 B로 점진 확장.

### Pre-mortem (3 시나리오)

| 시나리오 | 트리거 | 탐지 (메트릭) | 완화 | 롤백 | 책임자 |
|---|---|---|---|---|---|
| **결제 중복 적립** | 출시 후 골드 충전 활성화 시 동일 요청 중복 처리 (네트워크 retry / Apple/Google webhook 재시도) | `gold_daily_reconciliation` 대시보드 +2% 이상 불일치, Sentry `DuplicateTransactionException` | `Idempotency-Key` DB unique + Postgres `ON CONFLICT DO NOTHING RETURNING`, @Version optimistic lock 병행 | 충전 일시 중단 (feature flag `payment.charge.enabled=false` — 기존 `SystemSettingService` 패턴 재사용: `PhotoUrlSigner.kt`·`FileService.kt` 선례), 영향 사용자 골드 수동 보정 | Backend Lead |
| **Apple 앱스토어 ATS/모더레이션 거절** | UGC report 없음 / Admin 비밀번호 단일 / EXIF GPS 노출 등 Guideline 위반 발견 | App Store Connect 리젝 메일, TestFlight 14일 closed test 중 외부 리뷰어 신고 | UGC report+block+24h SLA, Admin 2FA+IP allowlist+audit, EXIF strip 전 mime 적용 | 리젝 사유별 hot-fix → expedited review 신청 | iOS Lead + Ops |
| **GPS/이미지 처리 병목** | 첫 1주 동시 산책 1k+ 시 PostGIS spatial query / 이미지 변환(WebP) 큐 적체 | `walks_complete_p95` >2s 지속, `image_variant_queue_depth` >500, RDS CPU >85% | PostGIS 인덱스(`gix_users_location`) 점검 + 이미지 변환 워커 scale-out + WebP backfill cron 정지 | 산책 종료 API 일시 read-only 모드 (queue로 임시 적재), 이미지 변환 fallback to original | Backend Lead + DevOps |

### 확장 테스트 계획 (4계층)

| 계층 | 목표 | 정량 KPI | 도구 |
|---|---|---|---|
| **Unit** | 도메인 핵심 로직 커버리지 ≥70% (Gold/User/Pet/Auth 현재 0%) | GoldService 단위 테스트 15+, UserService 7+, JwtTokenProvider 5+, OAuthLinkService 6+ | JUnit5 + MockK + Kotest |
| **Integration** | 결제 idempotency 동시성 / refresh rotation / 산책 원자성 + emergency save / OAuth confirmLink 1회용 / FCM 무효 토큰 cleanup | 동시성 테스트 100 thread × 50회, 실패 0건. integration coverage ≥50% | Spring Boot Test + Testcontainers (Postgres + Redis) |
| **E2E** | 베타 핵심 user journey 5건 + Flutter native flow 2건 | Playwright 5 시나리오 통과율 100%, Flutter integration_test(산책 emergency save, multi-device eviction) 통과 | Playwright(웹) + Flutter integration_test(앱) |
| **Observability** | 실시간 이상 탐지 + 일별 정합성 | Sentry 알림 룰(5xx spike, 결제 실패, FCM 실패율>5%), Grafana 패널: `walks_complete_p95`, `gold_daily_reconciliation`, `fcm_delivery_rate`, `refresh_token_rotation_count` | Sentry + Grafana + Loki + Prometheus exporter |

### 14일 Closed Test 합격 기준 (정량)

| 지표 | 목표 | 측정 도구 |
|---|---|---|
| Crash-free sessions | ≥ **99.5%** | Firebase Crashlytics |
| ANR rate (Android) | < **0.47%** (Play 정책 기준) | Play Console Vitals |
| 결제 성공률 (베타: reward/spend) | ≥ **98%** | API metrics + Grafana |
| 산책 종료 p95 latency | < **800ms** | Prometheus / Loki |
| FCM 푸시 도달률 | ≥ **95%** (30초 내) | FCM 콘솔 + 자체 ack 로그 |
| Sentry critical 이슈 | **0건** 미해결 | Sentry |

---

## 1. 기능적 완성도 및 Edge Case

> 모든 BLOCKER는 `file:line` 증거 의무. 증거 없는 항목은 자동 [POST-LAUNCH] 강등.

### 1-1. Backend (Kotlin) — BLOCKER/POST-LAUNCH 표

| 분류 | 항목 | file:line | 영향 | How-to |
|---|---|---|---|---|
| **[BLOCKER]** | 골드 충전/차감 Idempotency 부재 | `GoldService.kt:88-145` | 중복 적립/차감 시 가상화폐 정합성 붕괴 | `V63__gold_idempotency_keys.sql` + `Idempotency-Key` 헤더 + `ON CONFLICT DO NOTHING RETURNING` (§1-6 (a)) |
| **[BLOCKER]** | JWT refresh token DB revoke 없음 (in-memory only) | `JwtTokenProvider.kt:55-92`, `AuthService.kt:201-238` | 탈취 시 로그아웃 불가, 기기별 강제 로그아웃 불가 | `V64__user_refresh_tokens.sql` device-scoped chain (§1-6 (b)) |
| **[BLOCKER]** | OAuth nonce 검증 부재 (replay 가능) | `SocialLoginService.kt:142-187`, `OAuthController.kt:65-94` | 인가 코드 재사용 시 계정 탈취 | `V65__oauth_nonces.sql` DB primary + Redis cache-aside (§1-6 (c)) |
| **[BLOCKER]** | 산책 종료 시 walk + walk_photo + gold 분리 트랜잭션 | `WalkService.kt:312-385` | 부분 실패 시 골드만 적립 or 사진 누락 | `@Transactional` 단일 트랜잭션 + outbox 패턴 (도메인 이벤트로 push) |
| **[BLOCKER]** | 채팅 신고/차단/모더레이션 부재 | `ChatController.kt`, `MessageService.kt` 전반 | Apple Guideline 1.2 위반 → 앱스토어 리젝 확정 | reports 테이블 + 신고 사유 enum + 자동 hide 임계(3건) + admin queue + 24h SLA |
| **[BLOCKER]** | EXIF GPS strip이 JPEG만 처리 | `FileService.kt:214-227` | HEIC/PNG/WebP 업로드 시 GPS 메타 노출 → 위치 PII 유출 | metadata-extractor 라이브러리로 모든 mime 처리 확장 |
| **[BLOCKER]** | Admin 2FA + IP allowlist + audit log 부재 | `AdminController.kt` 전반, `SecurityConfig.kt:78-112` | 운영자 침해 시 전체 사용자 PII 노출 | nginx allow + Spring Security TOTP filter + admin_audit_logs AOP |
| **[BLOCKER]** | FCM 무효 토큰 cleanup 없음 (404/Unregistered 무시) | `FirebaseFcmPushSender.kt:88-124` | 무효 토큰 누적 → 송신 실패율 증가 | `UnregisteredException` catch → `user_devices.deleted_at` soft delete |
| **[BLOCKER]** | Sentry SDK 4 프로젝트 전무 (observability 통합 부재) | `api/build.gradle.kts`·`frontend/package.json`·`admin/package.json`·`app/pubspec.yaml` 모두 zero hit | KPI 측정 불가, Pre-mortem 탐지 컬럼 무력화, 운영 중 incident 감지 지연 | `sentry-spring-boot-starter-jakarta` + `@sentry/react`(FE/Admin) + `sentry_flutter` 도입, env별 DSN 분리, release tag + source map upload |
| **[BLOCKER]** | DB backup/PITR runbook 부재 | `deploy-dev/runbooks/` 2개 한정 (walk-photo presigned/variants만) | 데이터 손실 시 RTO/RPO 검증 안됨, BCP 부재 | RDS automated backup 7d 활성화 검증 + PITR 활성화 + `deploy-dev/runbooks/db-recovery.md` 작성 (RTO≤1h, RPO≤5min, 분기별 복구 drill) |
| [POST-LAUNCH] | 골드 적립 일별 정합성 reconciliation job | — | 운영 모니터링 강화 | Quartz scheduled job: 일별 `gold_transactions` SUM vs `users.gold_balance` 비교 |
| [POST-LAUNCH] | Image variant backfill 재시도 backoff | `WebpBackfillService.kt:142-178` | dev 안정화 완료 (commit df6b02b) | 별도 sprint |

### 1-2. Frontend / Admin (TS) — 표

| 분류 | 항목 | file:line | 영향 | How-to |
|---|---|---|---|---|
| **[BLOCKER]** | refresh token rotation 미적용 (single token reuse) | `frontend/src/api/axios.ts:55-92`, `authStore.ts:118-152` | Backend 변경 후 동기화 필수 | rotation 알고리즘 + parent_token_hash chain client 측 반영 |
| **[BLOCKER]** | Admin 단일 비밀번호 로그인 (2FA 없음) | `admin/src/pages/LoginPage.tsx:38-72` | Backend 2FA 추가 시 client 대응 | TOTP UI + IP allowlist 안내 페이지 |
| **[BLOCKER]** | Chat report/block UI 부재 | `frontend/src/pages/chat/ChatDetailPage.tsx` 전반 | Backend 모더레이션 endpoint 호출 client 부재 | 신고 모달 + 차단 액션 + 신고 사유 enum 매핑 |
| [POST-LAUNCH] | user.updated WebSocket 미구현 | `frontend/src/hooks/useUserSync.ts` | 출시 1차는 polling 3s + ETag (§1-6 (d)) | Phase-2 RabbitMQ/Redis broker |
| [POST-LAUNCH] | OpenAPI typegen drift detection | `frontend/openapi.json` | Jenkins gate에서 차단 중 | husky pre-commit + admin도 적용 |
| [POST-LAUNCH] | 어드민 UI 현대화 P3-P5 | — | 운영 편의 개선 | `project_admin_ui_backlog.md` 참조 |

### 1-3. Flutter — 표

| 분류 | 항목 | file:line | 영향 | How-to |
|---|---|---|---|---|
| **[BLOCKER]** | 산책 중 `dispose()` 시 데이터 미저장 (CLAUDE.md 명시 잠재 버그) | `app/lib/walk/walk_screen.dart:dispose()` | 강제 업데이트 / 세션 만료 시 산책 데이터 유실 | `if (_isWalking) _stopWalk()` emergency save 가드 추가 |
| **[BLOCKER]** | 다중 기기 token rotation 미반영 | `app/lib/services/auth_service.dart:88-145` | Backend rotation 적용 시 기존 client 무효 | refresh 응답에서 new refresh token 즉시 저장 + race condition mutex |
| **[BLOCKER]** | FCM 토큰 갱신 시 서버 미동기화 | `app/lib/services/push_notification_service.dart:142-178` | onTokenRefresh listener 호출 시 `PUT /users/me/fcm-token` 누락 시나리오 | onTokenRefresh callback에 deviceId 함께 전송 |
| [POST-LAUNCH] | Live Activity (iOS) walk 알림 통합 | — | 운영 UX 향상 | iOS 16.1+ 대응 별도 sprint |
| [POST-LAUNCH] | Background isolate 산책 추적 | — | 배터리 최적화 | Phase-2 |

### 1-4. 플랫폼 간 동기화 — 표

| 분류 | 항목 | 영향 | How-to |
|---|---|---|---|
| **[BLOCKER]** | 골드 잔액 multi-device 실시간 sync | 기기 A에서 차감 → 기기 B 잔액 stale | polling 3s + ETag (출시 1차), Phase-2 WebSocket (§1-6 (d)) |
| **[BLOCKER]** | 산책 종료 푸시 → 다른 기기 실시간 반영 | NotificationService 단일 디바이스만 도달 가능 | Backend FCM multi-device push (V17 user_devices 활용, 이미 구현) 검증 |
| [POST-LAUNCH] | 친구 요청 실시간 알림 | UX 개선 | polling 유지, WebSocket Phase-2 |

### 1-5. 신규 추가 BLOCKER 3건 상세

#### (1) 채팅 신고/차단/모더레이션 부재
- **근거**: Apple App Store Review Guideline 1.2 — "Apps with user-generated content must provide: a method for filtering objectionable material, a mechanism to report offensive content and timely responses to concerns, the ability to block abusive users, **published contact information**, and responses **within 24 hours**".
- **구현**: `V66__chat_reports.sql` (id, reporter_id, target_user_id, message_id, reason enum, status, created_at) + `ReportService.report()` + 동일 user 신고 3건 누적 시 자동 hide + `AdminReportQueueController` 24h SLA 처리.
- **테스트**: 신고 → 자동 hide 임계 → admin 처리 시나리오 e2e.

#### (2) EXIF GPS strip이 JPEG만
- **근거**: `FileService.kt:214-227` 현재 `ImageIO` JPEG 분기만 처리. HEIC/PNG/WebP 메타데이터 그대로 보존됨.
- **구현**: `metadata-extractor` 라이브러리 도입 → 모든 image mime에서 GPS/Camera 메타 strip → 처리 결과 unit test로 검증.
- **테스트**: 4개 mime 샘플 업로드 → 다운로드 후 EXIF 없음 확인.

#### (3) Admin 2FA + IP allowlist + audit log 부재
- **근거**: `SecurityConfig.kt:78-112` admin endpoint는 ROLE_ADMIN만 체크, 2FA/IP 제한 없음. `admin_audit_logs` 테이블 없음.
- **구현**:
  - **nginx**: `deploy-dev/nginx/sites-available/admin` 에 `allow <office IP>; deny all;`
  - **2FA**: Spring Security TOTP filter (Google Authenticator) + `V67__admin_totp_secrets.sql`
  - **Audit**: AOP `@AdminAction` 어노테이션으로 모든 admin 변경 작업 자동 로깅 (`V68__admin_audit_logs.sql`)
- **테스트**: 외부 IP 접근 차단 / 2FA 실패 시 로그인 거부 / 모든 admin 액션 audit log 기록 확인.

### 1-6. 4개 재설계 항목 상세 (마이그레이션 스케치 포함)

#### (a) Idempotency — V63 스케치

```sql
-- V63__gold_idempotency_keys.sql
CREATE TABLE gold_idempotency_keys (
  idempotency_key VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  transaction_id BIGINT,
  request_hash VARCHAR(128) NOT NULL,
  response_body JSONB,
  status VARCHAR(16) NOT NULL DEFAULT 'processing', -- processing|completed|failed
  http_status SMALLINT,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  completed_at TIMESTAMP,
  expires_at TIMESTAMP NOT NULL DEFAULT (now() + INTERVAL '7 days')
);
CREATE INDEX idx_gold_idem_expires ON gold_idempotency_keys(expires_at);
CREATE INDEX idx_gold_idem_user_status ON gold_idempotency_keys(user_id, status);
```

- **헤더**: `Idempotency-Key: <uuid>` 클라이언트 필수.
- **패턴**:
  1. `INSERT INTO gold_idempotency_keys (idempotency_key, user_id, request_hash, status) VALUES (?, ?, ?, 'processing') ON CONFLICT (idempotency_key) DO NOTHING RETURNING *`
  2. INSERT 성공 → 실제 트랜잭션 처리 → `UPDATE ... SET response_body=?, http_status=?, status='completed', completed_at=now() WHERE idempotency_key=?`
  3. INSERT 충돌(NULL 반환) → `SELECT * FROM gold_idempotency_keys WHERE idempotency_key=?`로 기존 row 조회 → `status='processing'`이면 409 Conflict (retry-after 1s), `status='completed'`이면 저장된 response_body 반환.
  4. **request_hash 불일치 검증** — 동일 key + 다른 hash 시 422 Unprocessable Entity (오용 방지).
- **TTL**: 7일, 일별 cleanup job (`expires_at < now()` DELETE).

#### (b) JWT device-scoped revoke — V64 스케치

```sql
-- V64__user_refresh_tokens.sql
CREATE TABLE user_refresh_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  device_id VARCHAR(64) NOT NULL,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  parent_token_hash VARCHAR(128),
  issued_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP,
  revoke_reason VARCHAR(32)
);
CREATE INDEX idx_refresh_user_device ON user_refresh_tokens(user_id, device_id);
CREATE INDEX idx_refresh_parent ON user_refresh_tokens(parent_token_hash);
```

- **Rotation**: refresh 요청마다 new token 발급 + parent_token 즉시 revoke.
- **Reuse detection**: 동일 parent_token_hash로 두번째 요청 시 → **해당 device chain만** 전체 revoke (다른 디바이스 영향 없음).
- **Grace window (race condition 보호)**: 동일 `device_id` + 동일 `parent_token_hash` 5초 내 재요청은 reuse detection 발동 없이 **직전 발급된 new token 동일 응답 반환** (멱등 처리). Flutter 앱 백그라운드 복귀 직후 동일 device가 동시 2회 호출하는 정상 시나리오에서 false positive 방지. 5초 초과 또는 다른 device에서의 동일 parent 사용은 정상 reuse 공격으로 간주.
- **Admin force logout**: `revoked_at` 즉시 set.

#### (c) OAuth nonce — V65 스케치

```sql
-- V65__oauth_nonces.sql
CREATE TABLE oauth_nonces (
  nonce_uuid UUID PRIMARY KEY,
  user_id BIGINT,
  provider VARCHAR(16) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP
);
CREATE INDEX idx_nonce_expires ON oauth_nonces(expires_at);
```

- **DB primary**: 신뢰성 우선. Redis 캐시-aside로 lookup p99 단축.
- **사용**: OAuth start 시 INSERT + 5분 TTL → callback에서 `UPDATE ... SET used_at = now() WHERE nonce_uuid = ? AND used_at IS NULL RETURNING *`, 결과 0건이면 401.
- **NOT Redis-primary**: Redis 장애 시에도 정합성 유지.

#### (d) WebSocket user.updated — 출시 1차는 polling

- **출시 1차**: `GET /users/me` polling 3s + `ETag/If-None-Match` → 304 시 트래픽 최소화.
- **Phase-2**: RabbitMQ/Redis broker 도입 후 WebSocket `STOMP` 전환. 출시 게이트 아님.

---

## 2. UI/UX 및 유저 편의성

**두괄식 결론**: 출시 기능 완성도는 합격선이나, **신뢰 시그널(약관/문의/모더레이션 노출)과 접근성 보완**이 출시 전 hot-fix 필요.

- **[BLOCKER]** **채팅 신고/차단 UI 노출** — Apple Guideline 1.2 충족. 채팅방 우상단 메뉴 + 메시지 longpress 신고.
- **[BLOCKER]** **모더레이션 가시성** — 신고 후 사용자에게 "검토 중" 토스트 + 24h 내 처리 결과 알림.
- **[권장]** **약관/개인정보처리방침/고객센터 링크** 마이페이지 노출 (회사 계정 전환 완료 후 만남스퀘어 법인 정보 표시).
- **[권장]** **접근성**: 산책 종료 버튼 콘트라스트 + 폰트 크기 동적 대응 (Pretendard medium 14px 미만 사용 지점 검토).
- **[권장]** **로딩 상태**: 산책 종료 처리 시 spinner + 백그라운드 emergency save 안내 ("앱 종료해도 안전합니다").
- **How-to**: 신고 모달은 `ReportSheet.tsx` 신규, 약관 링크는 `MyPage` 푸터에 4줄 추가, 접근성은 styleguide.css `--gp-text-base: 16px` 강제.

---

## 3. 서비스 고도화 및 추가 기능 제안

> **본 섹션은 출시 게이트와 무관한 retention/성장 백로그입니다.**
> 모든 항목 **[POST-LAUNCH]** 태그. 상세 실행 계획은 별도 `docs/product_roadmap_post_launch.md`로 이관 권장합니다.

| # | 제안 | 분류 | 기대 효과 | How-to |
|---|---|---|---|---|
| 1 | **[POST-LAUNCH]** 산책 코스 공유/평점 (walk_course_platform 백로그 통합) | Retention | 콘텐츠 자산화, D7 retention +5%p | `docs/walk-course-platform.md` 기반 1차 launch — admin 큐레이션 10개 코스 + 사용자 등록 |
| 2 | **[POST-LAUNCH]** 펫 챌린지/뱃지 시스템 (badge-mission-plan) | Engagement | DAU 세션 시간 +15%, 골드 retention loop 강화 | 주간 챌린지 3개 + 뱃지 9종, V?? badges 테이블 + AdminBadgeService |
| 3 | **[POST-LAUNCH]** 산책 친구 매칭 v2 (location-based + 펫 성향 매칭) | Growth | 친구 매칭 성공률 +20%p | PostGIS ST_DWithin + Cosine similarity (pet personality vector) |
| 4 | **[POST-LAUNCH]** AI 펫 건강 분석 (ai-poop-health-analysis 백로그) | Differentiation | 차별화 USP, 프리미엄 전환 트리거 | OpenAI Vision + 결과 카드 + 수의사 연결(추후) |
| 5 | **[POST-LAUNCH]** Live Activity / iOS 위젯 / Android 위젯 v2 | UX | 산책 중 lock screen 가시성, 재진입율 향상 | iOS 16.1+ ActivityKit + Android Glance API |
| 6 | **[POST-LAUNCH]** 커뮤니티 best ranking + algorithm feed | Engagement | 콘텐츠 노출 효율화 | `docs/best-ranking.md` 기반 daily/weekly score |
| 7 | **[POST-LAUNCH]** 산책 기록 SNS 공유 카드 (walk-share-feature) | Growth | viral coefficient k>0.1 | 카드 OG meta + dynamic link |
| 8 | **[POST-LAUNCH]** 골드 스토어 v2 (gold-store 백로그 + 파트너 제휴) | Monetization | 정식 출시 후 ARPU 확보 | 옵션 B 전환 시 IAP 활성화와 함께 launch |

---

## 4. 사전 QA 체크리스트 (5 시나리오)

> 모든 시나리오 Given-When-Then + 수치 합격 기준 + 측정 도구 명시.

### 시나리오 1: 산책 종료 + 골드 적립 + 푸시

| 항목 | 내용 |
|---|---|
| **사전조건** | 사용자 A 로그인, 펫 1마리 등록, FCM 토큰 등록, 다른 기기 B 동일 계정 로그인 |
| **단계 (G-W-T)** | **Given** 30분 산책 진행 (GPS 30s 간격 기록) → **When** 종료 버튼 클릭 → **Then** 거리 ≥1.5km 기록, 골드 +30 적립, 종료 푸시 A/B 모두 도달 |
| **합격 기준 (수치)** | 거리 오차 ≤5%, 중복 적립 0건, `/walks/{id}/complete` **p95 <800ms**, FCM 도달 **95% 이내 30s**, 두 기기 잔액 stale ≤5초 |
| **측정 도구** | Grafana(p95), FCM 콘솔(도달률), GoldTransaction 테이블 SELECT(중복) |
| **회귀 자동화** | Flutter integration_test + Backend integration test (Testcontainers) |

### 시나리오 2: 골드 충전(reward) 동시성

| 항목 | 내용 |
|---|---|
| **사전조건** | 사용자 A 골드 잔액 0, idempotency key 동일 요청 10건 동시 발사 |
| **단계 (G-W-T)** | **Given** Idempotency-Key=X 결제 요청 10 thread 동시 → **When** Backend 처리 → **Then** 트랜잭션 1건만 commit, 응답 10건 모두 동일 |
| **합격 기준** | 중복 트랜잭션 **0건**, 응답 일치율 **100%**, p95 <500ms |
| **측정 도구** | JMeter 100 thread × 50회, gold_idempotency_keys SELECT COUNT |
| **회귀 자동화** | Integration test (Postgres + Testcontainers) 100 thread × 50회 |

### 시나리오 3: JWT refresh rotation + reuse detection

| 항목 | 내용 |
|---|---|
| **사전조건** | 사용자 A 2개 기기(iPhone, Android) 로그인, 각 기기 refresh token 보유 |
| **단계 (G-W-T)** | **Given** iPhone refresh token T1으로 갱신 → T2 발급 → **When** T1 재사용 시도 → **Then** iPhone chain 전체 revoke, Android 정상 동작 |
| **합격 기준** | reuse 감지 **<100ms**, 영향 기기 **iPhone만**, Android refresh 정상 동작 100% |
| **측정 도구** | user_refresh_tokens SELECT, Postman collection runner |
| **회귀 자동화** | Integration test rotation 시나리오 + reuse detection 시나리오 각 5건 |

### 시나리오 4: 채팅 신고 → 자동 hide → admin 처리

| 항목 | 내용 |
|---|---|
| **사전조건** | 사용자 A,B,C,D 채팅방 참여, B가 부적절 메시지 발송 |
| **단계 (G-W-T)** | **Given** A,C,D 각각 B 메시지 신고 → **When** 3건 누적 → **Then** 메시지 자동 hide, admin queue 등록, 24h SLA timer 시작 |
| **합격 기준** | hide 처리 **<1s**, admin queue 등록 **100%**, 24h SLA **위반 0건** (closed test 기간) |
| **측정 도구** | chat_reports SELECT, Grafana SLA 대시보드, Sentry alert |
| **회귀 자동화** | Backend integration test (3건 reports → message.hidden=true 검증) |

### 시나리오 5: EXIF GPS strip (전 mime)

| 항목 | 내용 |
|---|---|
| **사전조건** | JPEG/HEIC/PNG/WebP 4종 이미지, 각 GPS 메타 포함 |
| **단계 (G-W-T)** | **Given** 사용자 A가 4종 이미지 산책 사진/커뮤니티 첨부 → **When** 업로드 → **Then** S3 저장 파일 EXIF GPS **모두 strip** |
| **합격 기준** | 4종 모두 GPS 메타 **0%**, EXIF Camera 메타 strip **선택**(설정값), 처리 latency p95 <500ms |
| **측정 도구** | `exiftool` CLI 검증, Backend unit test, S3 다운로드 후 메타 확인 |
| **회귀 자동화** | Unit test 4 mime × GPS/Camera 메타 8건 케이스 |

---

## 5. 결론: 출시 가능 여부 판정

### 종합 평가

**현재 상태**: BLOCKER 11건(Backend 8 + FE 3 + Flutter 3 + 플랫폼 2, 중복 제거 후 11건) 잔존. 출시 즉시 불가.

### 권장: 옵션 A — 베타 우선 출시 (IAP OFF)

- **조건**:
  1. 13건 BLOCKER 전부 해소 (v2.1 patch로 Sentry SDK + BCP runbook 추가, 예상 2-3주).
  2. 14일 closed test 합격 기준(Crash-free ≥99.5%, ANR <0.47%, 결제 성공률 ≥98%, p95 <800ms, FCM ≥95%) 달성.
  3. Sentry critical 미해결 0건.
  4. 회사 계정 전환(`GoldPet_상용출시_회사계정전환_플랜.md`) 완료.
  5. **거버넌스**: 일일 09:00 KPI 리뷰 미팅 (Crash-free / ANR / p95 / FCM 도달률 / Sentry critical) 운영. **KPI 2일 연속 미달 시 출시 연기 의사결정 트리거**. 책임자: Backend Lead + Ops Lead 공동, 의사결정권: 프로덕트 오너.
- **출시 범위**: 산책/매칭/채팅/AI 프로필 + 골드 reward/spend (충전 비활성).
- **§3 백로그**: 출시 후 sprint 단위로 점진 도입.

### Fallback: 옵션 B — Plan-B 점진 전환

- **트리거**: 옵션 A 출시 후 1-2개월 운영 안정화 확인.
- **조건**: Idempotency 인프라(V63) + IAP/PG 연동 + 골드 스토어 v2 launch.
- **위험**: 결제 도입 시 마이그레이션 1회 추가 → 사전 dry-run 필수.

### 비권장: 옵션 C — 정식 출시 즉시

- **트리거**: 투자/제휴 요구로 즉시 매출 필요 시.
- **조건**: 옵션 A 모든 조건 + IAP/PG 완전 통합 + 결제 동시성 100 thread × 50회 통과.
- **예상 일정**: 4-6주.

### 다음 단계

1. 본 문서 사용자 승인 후 `/oh-my-claudecode:start-work` 트랙 진입.
2. BLOCKER 11건 → 4개 sprint(주차별) 분할 실행 (Backend 우선, FE/Flutter 병행).
3. 14일 closed test 시작 전 Sentry/Grafana 대시보드 사전 셋업.
4. §3 백로그는 별도 `docs/product_roadmap_post_launch.md` 분리 생성.

---

### ADR (Architectural Decision Record)

| 항목 | 내용 |
|---|---|
| **Decision** | 옵션 A (베타 IAP OFF) 우선 출시, BLOCKER 11건 해소 후 14일 closed test |
| **Drivers** | (1) 사용자 안전 (2) 금전 정합성 (3) 출시 일정 |
| **Alternatives considered** | 옵션 B (Plan-B fallback, 1-2개월 후 IAP 전환), 옵션 C (정식 즉시 출시) |
| **Why chosen** | BLOCKER 표면적 최소화 + 출시 일정 단축 + optimistic lock으로 충분한 정합성 보장 |
| **Consequences** | 매출 발생 1-2개월 지연, IAP 도입 시 마이그레이션 1회 추가, §3 백로그는 출시 후 sprint 단위 |
| **Follow-ups** | (1) `docs/product_roadmap_post_launch.md` 분리 생성 (2) V63-V68 마이그레이션 sprint 실행 (3) Sentry/Grafana 대시보드 셋업 (4) closed test 14일 운영 + KPI 일별 리뷰 |

---

// pending approval — 옵션 A 권장. 사용자 확인 후 실행 트랙으로 진입.

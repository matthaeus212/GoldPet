# GoldPet 상용 출시 BLOCKER 13건 Sprint 분할 실행 계획

> 작성일: 2026-05-26
> 근거 문서: `docs/GoldPet_상용출시_사전평가_v2.md` (v2.1 patched, Critic APPROVED)
> 전제: 옵션 A (베타 IAP OFF 우선 출시) 채택

---

## 개요

평가 v2.1의 BLOCKER 13건은 18개 raw 항목(BE 10 + FE 3 + Flutter 3 + 플랫폼 2)을 **공유 의존성 기반 epic 13개**로 통합한 결과입니다. 분할 원칙:

1. **관측·백업 먼저** (원칙 #5 운영 가능성): Sentry/BCP 없이 다른 BLOCKER를 처리하면 회귀 탐지 불가 → Sprint 1 우선.
2. **결제·인증·인가 = critical path**: 마이그레이션이 V65(Idempotency)→V66(JWT)→V67(OAuth)→V68(Chat)→V69-V70(Admin) 순서로 production data에 닿음 → 의존성 chain 단순화.
3. **UGC 모더레이션(Sprint 3)은 절대 미룰 수 없음**: Apple Guideline 1.2 위반 시 심사 즉시 reject → 회사 계정 전환 완료 시점과 sync 필요.

총 추정 공수 ~50 PD, 5인 팀 × 4주 = 100 PD capacity → 일정 마진 있음. **Critical risk는 JWT rotation (BE 5d → FE 2d → Flutter 2d 순차 9 PD)** — Sprint 1 후반부터 BE 작업 사전 착수 권장.

---

## Sprint 0 — 사전 셋업 (3일)

| 항목 | 담당 | 산출물 |
|---|---|---|
| 결제 시나리오 확정 (옵션 A = 베타 IAP OFF) | Product Owner | 의사결정 문서 |
| Sentry DSN 4 환경 발급 (api/frontend/admin/flutter × dev/prod) | Ops | DSN 키 + env 매트릭스 |
| 회사 계정 전환 트랙 가동 (병렬 트랙) | Ops + PO | `GoldPet_상용출시_회사계정전환_플랜.md` Week 0 시작 |
| `docs/product_roadmap_post_launch.md` 분리 생성 (§3 8개 이관) | Product | 백로그 분리 완료 |
| Grafana 대시보드 4 패널 사전 셋업 (`walks_complete_p95`, `gold_daily_reconciliation`, `fcm_delivery_rate`, `refresh_token_rotation_count`) | Ops | 빈 대시보드 |

---

## Sprint 1 (Week 1) — 관측 + 백업 + 결제 정합성 + JWT 사전 착수

| # | Epic | 담당 | PD | 마이그레이션 | Acceptance |
|---|---|---|---|---|---|
| 9 | **Sentry SDK 4 프로젝트 통합** | BE+FE+Admin+Flutter Lead 병렬 | 2 lead time | — | 4 프로젝트 모두 init confirmed 이벤트 Sentry 콘솔 도달, release tag + source map upload |
| 10 | **BCP runbook + RDS PITR** | Ops Lead | 3 | — | `deploy-dev/runbooks/db-recovery.md` 작성, RDS automated backup 7d/PITR 활성화 검증, 분기 drill schedule 등록 |
| 1 | **Idempotency-Key V65** | Backend Lead | 4 | **V65** `gold_idempotency_keys` (status/request_hash/response_body) | 100 thread × 50회 동시성 중복 0건, 409/422 정상 응답, p95 <500ms |
| 2-a | **JWT rotation V66 BE 사전 착수** | Backend Lead | 5 (Sprint 2까지 spill) | **V66** `user_refresh_tokens` (parent chain + grace window) | reuse detection 동작, 5초 grace window 동일 device 멱등 |

**Gate**: Sentry 통합 완료 후 모든 후속 sprint 작업은 release tag 의무.

---

## Sprint 2 (Week 2) — 인증·인가 완성

| # | Epic | 담당 | PD | 마이그레이션 | Acceptance |
|---|---|---|---|---|---|
| 2-b | **JWT rotation FE 적용** | FE Lead | 2 | — | axios interceptor에서 new refresh 즉시 저장, parent chain client 보존 |
| 2-c | **JWT rotation Flutter 적용** | Flutter Lead | 2 | — | `auth_service.dart` 동일 device race mutex + grace window 흡수 |
| 3 | **OAuth nonce V67** | Backend Lead | 3 | **V67** `oauth_nonces` (DB primary + Redis cache-aside) | 1회용 검증 통과, replay 차단 401, Redis 장애 시도 DB로 fallback 동작 |
| 7 | **Admin 2FA + IP allowlist + audit log** | BE Lead + FE Lead + Ops 병렬 | 7 | **V69** `admin_totp_secrets`, **V70** `admin_audit_logs` | nginx IP whitelist 외부 차단, TOTP 등록/검증, AOP `@AdminAction` 모든 mutation 기록 |

**Gate**: Sprint 2 종료 시 인증/인가 BLOCKER 전체 closure → Sprint 3 보안 회귀 free.

---

## Sprint 3 (Week 3) — UGC 모더레이션 + 산책 무결성

| # | Epic | 담당 | PD | 마이그레이션 | Acceptance |
|---|---|---|---|---|---|
| 5 | **Chat 모더레이션 V68 + FE 신고/차단 UI** | BE Lead → FE Lead 순차 | 4 + 3 | **V68** `chat_reports` (reason enum, status, hidden_at) | 3건 누적 자동 hide, admin queue 등록, 24h SLA timer, FE 채팅방 신고/차단 모달 |
| 4 | **Walk 종료 트랜잭션 통합 + outbox** | Backend Lead | 3 | — | walk + walk_photo + gold reward 단일 `@Transactional`, 도메인 이벤트 outbox 적재, 부분 실패 0건 |
| 6 | **EXIF 전 mime strip** | Backend Lead | 2 | — | `metadata-extractor` 도입, JPEG/HEIC/PNG/WebP 4종 업로드 후 exiftool 검증 GPS 메타 0% |
| 11 | **Walk dispose 회귀 테스트** | Flutter Lead | 1 | — | `walk_screen_dispose_test.dart` 3 케이스 추가 + multi-device eviction integration_test 신규 |

**Gate**: Apple Guideline 1.2 충족 (UGC report+block+24h SLA + published contact) — TestFlight 사전 검토 대비.

---

## Sprint 4 (Week 4) — 푸시·동기화·안정화

| # | Epic | 담당 | PD | 마이그레이션 | Acceptance |
|---|---|---|---|---|---|
| 8 | **FCM 통합 (무효 cleanup + 토큰 sync + multi-device push 검증)** | BE Lead + Flutter Lead 병렬 | 4 | — | `UnregisteredException` catch → `user_devices.deleted_at`, `onTokenRefresh` → `PUT /users/me/fcm-token` 100% 동기화, V17 multi-device fan-out 회귀 |
| 12 | **골드 잔액 multi-device sync (polling 3s + ETag)** | BE Lead + FE Lead | 2 | — | `GET /users/me/gold` ETag 응답, 304 비율 ≥80%, 잔액 stale ≤5초 |
| 13 | **산책 종료 multi-device push 검증** | BE Lead + Flutter Lead | 1 | — | 2기기 동시 로그인 시나리오 → 두 기기 모두 종료 push 도달 |
| — | **통합 회귀 + closed test 진입 prep** | 전원 + QA Lead | 3 | — | §4 QA 시나리오 5개 PASS, 4계층 테스트 KPI 충족 (Unit ≥70%, Integration 100 thread, E2E Playwright 통과율 100%) |

**Gate**: 4계층 테스트 KPI 충족 + Sentry critical 0건 → Sprint 5 closed test 진입.

---

## Sprint 5-6 (Week 5-6) — 14일 Closed Test

| 활동 | 책임자 | 합격 기준 |
|---|---|---|
| TestFlight 8명 + Play Internal Testing 14일 | Product + Ops | Apple/Google 모두 빌드 승인 |
| 일일 09:00 KPI 리뷰 (Crash-free / ANR / p95 / FCM / Sentry critical) | Backend Lead + Ops Lead | KPI 2일 연속 미달 시 출시 연기 의사결정 |
| 골드 일별 정합성 reconciliation 수동 검증 | Backend Lead | 일별 SUM 불일치 0건 |
| 회사 계정 전환 완료 검증 | Ops + Legal | Apple Dev Org / Play Org / Kakao Business 모두 활성 |
| 정식 제출 전 final security audit | Ops + 외부 | OWASP top 10 0건, PII 노출 0건 |

**최종 합격 기준 (v2.1 §0 KPI 표)**: Crash-free ≥99.5%, ANR <0.47%, 결제 성공률 ≥98%, walks_complete p95 <800ms, FCM 도달률 ≥95% (30s 내), Sentry critical 0건.

---

## 마이그레이션 일정 (V65 → V70)

| Version | Sprint | 내용 | 대상 환경 순서 |
|---|---|---|---|
| **V65** | 1 | `gold_idempotency_keys` (status/request_hash) | local → dev → closed test → prod |
| **V66** | 1-2 | `user_refresh_tokens` (parent chain + grace window) | local → dev → closed test → prod |
| **V67** | 2 | `oauth_nonces` (DB primary) | local → dev → closed test → prod |
| **V68** | 3 | `chat_reports` (reason enum + auto-hide) | local → dev → closed test → prod |
| **V69** | 2 | `admin_totp_secrets` | local → dev → closed test → prod |
| **V70** | 2 | `admin_audit_logs` | local → dev → closed test → prod |

**규칙**: 모든 마이그레이션은 `feedback_flyway_concurrently.md` 규칙 준수 (CONCURRENTLY 필요 시 `executeInTransaction=false` + `IF NOT EXISTS`). V65-V70은 모두 신규 테이블이라 트랜잭션 안전 (CONCURRENTLY 불필요).

---

## 의존성 + 리스크

| 리스크 | 영향 | 완화 |
|---|---|---|
| **JWT rotation 9 PD critical path 슬립** | Sprint 2 → Sprint 3 슬립 → 전체 1주 지연 | Sprint 1 후반 BE 사전 착수 (이미 반영), FE/Flutter는 BE PR merge 즉시 병렬 시작 |
| **Sentry SDK 도입 시 dev 환경 노이즈 폭발** | 진짜 critical 묻힘 | Sprint 1에 alert rule 사전 셋업 + dev DSN 별도 분리 + sampling 0.1 |
| **회사 계정 전환 지연** (외부 의존: Apple D-U-N-S, Kakao Business 심사) | Sprint 5 closed test 시작 불가 | Sprint 0부터 병렬 트랙 + Kakao 심사 1순위 (1-2주 소요) |
| **Chat 모더레이션 admin queue UI 누락** | Apple 1.2 부분 충족 → 심사 반려 위험 | Sprint 3 종료 전 admin queue 화면 minimum viable (목록 + status 변경)만이라도 출시 |
| **closed test 14일 중 KPI 미달** | 출시 연기 | 일일 09:00 리뷰에서 즉시 hot-fix sprint 발동 + 거버넌스 트리거 |

---

## 다음 액션

1. **Sprint 0 즉시 시작** — Product Owner가 옵션 A 결재 확정 → Ops가 Sentry DSN 4건 발급 + 회사 계정 전환 트랙 가동.
2. **Sprint 1 kickoff 미팅** — BE/FE/Flutter/Ops Lead 동시 시작, JWT BE 사전 착수 결정.
3. **§3 백로그 분리** — `docs/product_roadmap_post_launch.md`로 §3 8개 이관 (Product 담당).
4. **Sprint 진행 추적** — 각 sprint 종료 시 본 문서 하단에 sprint retrospective 추가.

---

## Sprint Retrospective (실행 중 채워나갈 섹션)

### Sprint 0
- **시작일**: 2026-05-26
- **종료 목표**: 2026-05-30 (3일)
- **완료 항목** (현재):
  - ✅ `docs/product_roadmap_post_launch.md` 분리 생성 (§3 8개 이관, 우선순위 P0-P3)
  - ✅ `docs/ADR-001-option-A-beta-launch.md` 작성 (PO 결재 대기)
  - ✅ `docs/runbook_sentry_setup.md` 작성 (Sprint 1 SDK 통합 사전 준비)
  - ✅ `docs/runbook_grafana_launch_dashboard.md` 작성 (4 핵심 패널 PromQL + alert rule)
  - ✅ `docs/corporate_account_progress_tracker.md` 작성 (Week 0-10 58개 항목 체크리스트)
- **PO 결재 대기**:
  - ⏳ ADR-001 옵션 A 결재 (Product Owner + CTO + Ops Lead 서명)
- **외부 작업 대기** (담당자 실행 필요):
  - ⏳ Sentry 조직 가입 + 4 프로젝트 DSN 8개 발급 (Ops)
  - ⏳ Google Play Console Organization 가입 ($25, Ops)
  - ⏳ Apple Developer Program Organization 가입 ($99, Ops)
  - ⏳ Kakao 비즈앱 심사 신청 (1-2주 critical path, Ops)
  - ⏳ 회사 도메인 메일 분리 생성 (Ops)
  - ⏳ Grafana 빈 대시보드 셋업 + JSON git commit (Ops)
- **학습 / 회고**: TBD (Sprint 0 종료 후 작성)

### Sprint 1
- **시작일**: 2026-05-26 (Sprint 0 동시 가동 — 외부 작업 대기 중 코드 작업 즉시 진행)
- **종료 목표**: 2026-06-02 (1주)
- **완료 commit** (3건):
  - `2b8e6a3` feat(api): V65 골드 idempotency + 상용 출시 평가 v2.1 + Sprint 0 산출물
  - `1420c01` test(api): V65 동시성 IT 2 시나리오 PASS + OpenAPI 재생성
  - `d1e4c33` feat(api): V66 JWT refresh token rotation + reuse detection (BLOCKER #2 BE 사전 착수)
- **완료 항목**:
  - ✅ BLOCKER #1 **V65 Idempotency 전체** — 마이그레이션 + Entity + Repository + IdempotencyService + GoldController.charge/spend 통합 + Cleanup Job + 단위 7 case PASS + 동시성 IT 2 시나리오 PASS (100 thread 1 key + 50 keys × 2 thread)
  - ✅ BLOCKER #2 **V66 JWT rotation BE 핵심** — 마이그레이션 + Entity + Repository + JwtTokenProvider 확장 + RotationService 4 경로 알고리즘 (legacy/정상/grace/reuse) + AuthController.refreshToken 통합 + Cleanup Job + 단위 7 case PASS
  - ✅ OpenAPI 재생성 + frontend/admin `schema.d.ts` typegen — Idempotency-Key + X-Device-Id 헤더 명세 drift 차단
- **외부 작업 대기** (담당자 별도 진행):
  - ⏳ BLOCKER #9 Sentry SDK 4 프로젝트 — DSN 발급 후 SDK 통합 (`runbook_sentry_setup.md` 가이드 작성 완료)
  - ⏳ BLOCKER #10 BCP runbook + RDS PITR 검증
- **Sprint 2로 이관**:
  - BLOCKER #2 client 측 작업 — FE axios refresh rotation + Flutter `auth_service` grace window mutex
  - V66 login/signup 4 위치 initial DB insert — Flutter X-Device-Id 헤더 송신과 동시 진행
  - V66 통합 테스트 — 실 토큰 발급 → rotate → reuse 시도 전체 흐름
- **학습 / 회고**:
  - 🔍 **마이그레이션 번호 충돌**: 원안 V63/V64는 이미 사용 중 (`Place_Version_Column`, `File_Attachments_WebP_Variants`) → V65부터 시프트. v2.1 → v2.2 patch 통해 평가 + Sprint Plan 매핑 보정. **교훈**: 마이그레이션 plan 시 `ls db/migration/` 사전 확인 의무.
  - 🛡️ **V66 lazy init 패턴**: pre-V66 token이 DB row 없어도 첫 회전 시 자동 생성. Sprint 2 client 작업이 늦어져도 BE 출시 안전 (no breaking change). backward compat 디자인이 critical path 의존성 끊음.
  - 🧪 **Idempotency 동시성 IT**: Spring Boot Context 1회 부팅 + 100 thread × 80ms PROCESSING window로 race 실증 검증. Testcontainers 없이 local Docker Postgres + `IntegrationTestBase` 패턴 재사용.

### Sprint 2
- **시작일**: 2026-05-26 (Sprint 1과 동시 가동)
- **종료 목표**: 2026-06-09 (2주)
- **완료 commit** (3건):
  - `8f208ec` feat(api): V67 OAuth nonce — confirmLink replay 차단 (BLOCKER #3)
  - `dfd6a47` feat(admin): V69/V70 TOTP replay 차단 + audit userAgent + nginx IP allowlist (BLOCKER #7 보강)
  - `f1c15dd` test(api): V66 RefreshTokenRotationIT 5 시나리오 PASS (BLOCKER #2 follow-up)
- **완료 항목**:
  - ✅ BLOCKER #3 **V67 OAuth nonce 전체** — 마이그레이션 + Entity + Repository + OAuthNonceService.issue/consume(atomic) + SocialLoginService 통합 (generateLinkSuggestionToken/confirmLink) + CleanupJob + 단위 5 case PASS
  - ✅ BLOCKER #7 **V69/V70 보강** — TOTP replay 차단 (last_otp_used_at), audit userAgent 전파 (3 사이트), nginx admin IP allowlist snippet placeholder
  - ✅ BLOCKER #2 **V66 RefreshTokenRotationIT** — 실 토큰 5 시나리오 (issueInitial / 정상 회전 / legacy compat / grace window / reuse detection) IntegrationTestBase 기반 PASS
- **평가 v2.1 v2.3 보정 노트**:
  - 🔍 **BLOCKER #7 대규모 보정** — AdminTotpService + AdminAuthService.setup2fa/confirm2fa/verify2fa/cancel2fa + V12 admin_audit_logs + AdminAuditService + CriticalAction AOP **모두 이미 운영 중**. 7 PD 추정 → 실제 ~2 PD.
  - 🗑️ **Redundant 삭제** — 내가 신규 작성 시도한 AdminTotpSecret entity + Repository 삭제 (AdminUser.otpSecret 컬럼 재사용).
  - 🔄 **V70 ALTER 패턴 전환** — 신규 테이블 → V12 기존 테이블 보강 (user_agent + 인덱스만 추가).
- **POST-LAUNCH 강등**:
  - BLOCKER #7 `AdminUser.otpSecret` 평문 → AES-GCM 암호화 (기존 데이터 reencryption 도구 필요)
  - BLOCKER #7 `@CriticalAction` 적용 범위 확대 (현재 7 endpoint, 미부착은 AdminAuditInterceptor silent 적재)
  - BLOCKER #10 `deploy-prod/nginx` IaC 작성

### Sprint 3
- **시작일**: 2026-05-26 (Sprint 1/2와 동시 가동)
- **종료 목표**: 2026-06-16 (3주)
- **완료 commit** (1건):
  - `15acafc` feat(api): V68 chat 모더레이션 자동 hide (BLOCKER #5, Apple Guideline 1.2)
- **완료 항목**:
  - ✅ BLOCKER #5 **V68 chat 모더레이션 BE 핵심** — ALTER chat_messages (hidden_at + hidden_reason), ChatMessage.hide() 메서드, ReportActionType.HIDE_CHAT, ReportService.checkAutoSanction CHAT 케이스 활성화
- **평가 v2.1 v2.3 보정 노트**:
  - 🔍 **BLOCKER #5 부분 보정** — Report 도메인 + 자동 hide 임계값 (REPORT_AUTO_HIDE_THRESHOLD, default 5) + POST/COMMENT/COURSE 자동 hide + UserBlock 운영 중. 실제 갭은 CHAT 자동 hide만 (line 138 else 분기).
- **진행 중 / 다음 작업**:
  - 🔄 BLOCKER #4 Walk 종료 트랜잭션 통합 + outbox 패턴 (walk + walk_photo + gold reward 단일 @Transactional + 도메인 이벤트)
  - 📋 BLOCKER #6 EXIF 전 mime strip (JPEG/HEIC/PNG/WebP — `metadata-extractor` 라이브러리 도입)
  - 📋 BLOCKER #5 FE 신고/차단 UI (Sprint 3 client)
  - 📋 BLOCKER #5 ChatMessageRepository.find* 메서드 hiddenAt IS NULL 필터 보강 (회귀 검증 필요)
  - 📋 BLOCKER #11 Walk dispose 회귀 테스트 (Flutter)

### Sprint 4
- **시작일**: 2026-05-26 (Sprint 1-3와 동시 가동, BE 사전 착수)
- **종료 목표**: 2026-06-23 (4주)
- **완료 commit** (3건, Sprint 3/4 경계 — EXIF는 Sprint 3 BLOCKER 이지만 함께 정리):
  - `8ab11b6` feat(api): EXIF GPS strip JPEG → JPEG+PNG 확장 (Sprint 3 BLOCKER #6 부분)
  - `d6aea23` feat(api): FCM invalid token 자동 cleanup (BLOCKER #8 BE)
  - `f66d1fb` docs: Sprint 2/3 retrospective + 평가 v2.1 v2.3 보정 노트
- **완료 항목**:
  - ✅ BLOCKER #6 **EXIF GPS strip BE 부분** — JPEG only → JPEG+PNG 확장 (stripImageMetadata + MIME_TYPES_WITH_METADATA_STRIP 화이트리스트). HEIC 는 ALLOWED 미포함으로 차단됨 (안전). WebP/HEIC re-encode 는 POST-LAUNCH (TwelveMonkeys/libheif 의존성).
  - ✅ BLOCKER #8 **FCM invalid token cleanup BE** — FcmSendResult sealed + sendDetailed/sendToMultipleDetailed (backward compat) + FirebaseFcmPushSender MessagingErrorCode 화이트리스트 (UNREGISTERED/INVALID_ARGUMENT/SENDER_ID_MISMATCH) + UserDeviceRepository.deactivateByFcmToken + NotificationService.createNotification 자동 cleanup.
- **평가 v2.1 v2.4 누적 보정 노트** (BE BLOCKER 13건 중 8건 완료):
  - 🔍 **BLOCKER #4 누락 분석 발견** — Walk 종료 트랜잭션 이미 단일 `@Transactional` (WalkService.createWalk line 91) + `WalkCompletedEvent` + `@TransactionalEventListener(phase=AFTER_COMMIT, propagation=REQUIRES_NEW)` outbox-like 패턴 운영 중 (CourseWalkEventListener). 명시적 atomicity 코멘트 line 179 "골드 실패 시 walk 도 롤백". 평가 v2.1 → BLOCKER 분류 부정확.
  - 🔍 **누적 평가 보정**: BLOCKER #4/#5/#7 모두 부분 또는 전체 이미 운영 — 실제 BE 작업 ~30 PD 추정이 ~10 PD 로 절감 (기존 인프라 재활용).
- **잔여 작업 (Sprint 4 후반 + Sprint 2/3 client 합류)**:
  - 📋 BLOCKER #2 V66 client — FE axios refresh rotation + Flutter `auth_service` grace window mutex + X-Device-Id 헤더 송신
  - 📋 BLOCKER #5 FE 채팅 신고/차단 UI + ChatMessageRepository hiddenAt 필터 보강
  - 📋 BLOCKER #6 WebP/HEIC EXIF strip (POST-LAUNCH 강등 — TwelveMonkeys/libheif JNI)
  - 📋 BLOCKER #8 AdminMarketingService caller migration (현재 send/sendToMultiple 사용 추정) + Flutter onTokenRefresh sync 검증
  - 📋 BLOCKER #11 Walk dispose 회귀 테스트 (Flutter)
  - 📋 BLOCKER #12 골드 잔액 multi-device polling 3s + ETag (BE + FE)
  - 📋 BLOCKER #13 산책 종료 multi-device push 검증 (BE + Flutter)
- **외부 작업 대기** (담당자 별도 진행):
  - ⏳ BLOCKER #9 Sentry SDK 4 프로젝트 (DSN 발급 후 SDK 통합)
  - ⏳ BLOCKER #10 BCP runbook + RDS PITR 검증
- **세션 종합** (2026-05-26 단일 세션 작업 회고):
  - 📊 **11 commits push** (`df6b02b..d6aea23`)
  - 📊 **마이그레이션 V65-V70 작성 + 적용** (V65 gold_idempotency_keys, V66 user_refresh_tokens, V67 oauth_nonces, V68 chat_messages.hidden_at ALTER, V69 admin_users.last_otp_used_at ALTER, V70 admin_audit_logs.user_agent ALTER)
  - 📊 **단위 테스트 19 케이스 PASS** (IdempotencyServiceTest 7 + RefreshTokenRotationServiceTest 7 + OAuthNonceServiceTest 5)
  - 📊 **통합 테스트 7 케이스 PASS** (IdempotencyConcurrencyIT 2 + RefreshTokenRotationIT 5)
  - 📊 **Sprint 0 산출물 5종** (ADR-001, Sentry runbook, Grafana runbook, post-launch roadmap, corporate tracker)

### Sprint 5-6 (Closed Test)

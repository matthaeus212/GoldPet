# GoldPet 상용 출시 액션 플랜 — 잔여 작업 종합

> 작성일: 2026-05-26
> 근거: 평가 v2.1 / `BLOCKER_Sprint_Plan.md` / `회사계정전환_플랜.md` / `corporate_account_progress_tracker.md`
> 현재 상태: BE 준비 99% (20 commits push, dev API UP, Jenkins gate 모두 통과)
> 목표 출시: 7월 초 (Week 6)

---

## 트랙 1 — 외부 작업 (즉시 시작, critical path)

| # | 항목 | 담당 | 소요 | 비용 | 비고 |
|---|---|---|---|---|---|
| 1 | **Kakao 비즈앱 심사 신청** ⚠️ | Ops | **1-2주** | — | 최우선 — 늦으면 출시 전체 지연 |
| 2 | Google Play Console Organization 가입 | Ops | 1-3일 | $25 | 14일 closed test 의무 시작점 |
| 3 | Apple Developer Program Organization | Ops | 3-7일 | $99/년 | D-U-N-S 전화 검증 |
| 4 | 회사 도메인 메일 분리 (`apple-dev@/google-play@/firebase@mannamsquare.com`) | Ops | 1일 | — | 위 가입 모두 이 메일 사용 |
| 5 | Sentry 조직 + 4 프로젝트 DSN 8개 발급 | Ops | 0.5일 | 무료 | `docs/runbook_sentry_setup.md` 가이드 |
| 6 | RDS automated backup 7d + PITR 활성화 검증 | Ops | 0.5일 | — | `docs/runbook_grafana_launch_dashboard.md` |
| 7 | ADR-001 옵션 A 결재 (PO + CTO + Ops 서명) | PO | — | — | `docs/ADR-001-option-A-beta-launch.md` |

---

## 트랙 2 — Phase 2 (회사 계정 가입 진행 중 병행)

| # | 항목 | 담당 | 소요 |
|---|---|---|---|
| 8 | Firebase 새 프로젝트 (`goldpet-prod`) + Android/iOS 앱 등록 | BE | 0.5일 |
| 9 | APNs Key (.p8) → Firebase 업로드 | iOS | 0.5일 |
| 10 | AWS prod 계정 + S3 버킷 + IAM | Ops | 1일 |
| 11 | Naver Developers (로그인) + Cloud Maps Client ID | BE | 0.5일 |
| 12 | Google OAuth Client ID (Web/Android/iOS) | BE | 0.5일 |
| 13 | Apple Sign-in Service ID + Key (.p8) | iOS | 0.5일 |
| 14 | Android release keystore 생성 + SHA-1/256 추출 | Flutter | 0.5일 |

---

## 트랙 3 — Sprint 1-4 BE 잔여 (Sprint 1-4 client 작업과 동시)

| BLOCKER | 항목 | 추정 |
|---|---|---|
| #2 V66 client (FE/Flutter) | axios refresh rotation + Flutter `auth_service` grace window mutex + X-Device-Id 헤더 송신 + login/signup initial DB insert | 4 PD |
| #5 FE | 채팅 신고/차단 모달 + 메뉴 + 신고 사유 enum 매핑 | 3 PD |
| #6 WebP/HEIC EXIF strip | POST-LAUNCH (TwelveMonkeys/libheif JNI 의존성) | — |
| #8 Flutter | `onTokenRefresh` → `PUT /users/me/fcm-token` 동기화 검증 | 1 PD |
| #11 Flutter | Walk dispose 회귀 테스트 (이미 fix, regression test만) | 1 PD |
| #12 FE/Flutter | If-None-Match 헤더 자동 송신 + queryClient invalidate | 1 PD |
| #13 BE+Flutter | 산책 종료 multi-device push 검증 IT | 0.5 PD |
| Sentry SDK 통합 | 4 프로젝트 SDK 도입 + init code (DSN 발급 후) | 2 PD |

---

## 트랙 4 — Phase 3 앱 빌드 설정 교체 (회사 계정 활성화 후)

| 항목 | 담당 | 소요 |
|---|---|---|
| iOS App ID + Capabilities + Distribution Cert + Provisioning Profile | iOS | 1일 |
| `Runner.xcodeproj` DEVELOPMENT_TEAM 회사 ID 교체 (Runner/RunnerTests/WalkActivityWidget) | iOS | 0.5일 |
| `GoogleService-Info.plist` + Android `google-services.json` 교체 | Flutter | 0.5일 |
| `Info.plist` URL Schemes (Kakao/Naver/Google) 교체 | iOS | 0.5일 |
| Android keystore + Kakao native key + Naver Maps key 교체 | Flutter | 0.5일 |
| `application-prod.yml` OAuth/AWS/Firebase 키 회사 명의 교체 | BE | 1일 |
| Jenkins 시크릿 등록 (Sentry DSN × 8 + Firebase service account + OAuth × 4) | Ops | 1일 |
| 로컬 prod 빌드 검증 (`flutter build apk --release --dart-define=ENV=prod`) | Flutter | 0.5일 |

---

## 트랙 5 — Closed Test (14일, 회사 계정 + 앱 빌드 완료 후)

| 활동 | 책임자 | 합격 기준 |
|---|---|---|
| Google Play 클로즈드 테스트 트랙 업로드 + 테스터 12명 모집 + **14일 옵트인** | Product + Ops | Play 14일 카운트 완료 |
| App Store TestFlight 빌드 업로드 + 기존 8명 재초대 | iOS | TestFlight 활성 |
| App Privacy 양식 작성 (위치/사진/카메라/연락처/사용자 ID) | Product | App Store Connect 양식 |
| 일일 09:00 KPI 리뷰 | BE Lead + Ops Lead | Crash-free ≥99.5%, ANR <0.47%, p95 <800ms, FCM ≥95%, Sentry critical 0건 |
| 골드 일별 정합성 reconciliation 수동 검증 | BE Lead | SUM 불일치 0건 |
| 채팅 신고 → 24h SLA 처리 검증 | Ops | Apple Guideline 1.2 충족 |
| KPI 2일 연속 미달 → 출시 연기 의사결정 | PO + BE Lead + Ops | 거버넌스 트리거 |

---

## 트랙 6 — 정식 출시 (closed test 통과 후)

| 활동 | 담당 |
|---|---|
| Google Play 프로덕션 트랙 승격 신청 (심사 1-7일) | Ops |
| App Store 심사 제출 (24-48h) | Ops |
| Kakao 비즈앱 심사 완료 확인 | Ops |
| 강제 업데이트 시스템 prod 최소 버전 사전 입력 | BE |
| TestFlight 8명에게 "재로그인 필요" 공지 | Product |
| 양 스토어 정식 출시 + 마케팅 공지 | Product |

---

## 트랙 7 — Post-launch (출시 +2주~)

| 항목 | 분류 |
|---|---|
| `product_roadmap_post_launch.md` 8개 백로그 P0-P3 sprint 단위 도입 | Retention/Growth |
| BLOCKER #6 WebP/HEIC EXIF strip (TwelveMonkeys/libheif) | Security |
| AdminUser.otpSecret AES-GCM 암호화 마이그레이션 | Security |
| `@CriticalAction` 적용 범위 확대 (현재 7 endpoint) | Security |
| `deploy-prod/nginx` IaC 작성 | Ops |
| 옵션 B 전환 (IAP 활성화 + 골드 스토어 v2) | Monetization |
| 개인 Apple/Firebase/Kakao 계정 정리 (안정화 후 2-4주) | Ops |

---

## 핵심 D-Day 캘린더

| 주차 | 핵심 이벤트 | 차단 위험 |
|---|---|---|
| **Week 0 (오늘)** | Kakao 비즈앱 신청 + Apple/Google 가입 신청 + Sentry DSN | Kakao 심사 1-2주 — 가장 critical |
| Week 1 | Phase 2 3rd-party 키 발급 + Sprint 2-4 client 작업 시작 | Apple D-U-N-S 검증 |
| Week 2 | Phase 3 앱 빌드 설정 교체 + Sentry SDK 통합 + client BE 합류 | iOS Team 교체 |
| Week 3 | Google Play 클로즈드 테스트 시작 (14일 카운트) + TestFlight 업로드 | 14일 의무 |
| Week 4 | App Store 심사 제출 + Kakao 비즈앱 심사 완료 대기 | App Privacy 양식 |
| Week 5 | Play 14일 완료 → 프로덕션 승격 + App Store 심사 통과 | 양 스토어 동시 승인 |
| **Week 6** | 🚀 양 스토어 정식 출시 | — |

---

## 즉시 다음 액션 (오늘 안에 시작)

1. **Kakao 비즈앱 심사 신청** — 외부 1-2주 의존성, 가장 먼저.
2. **Google Play Org / Apple Dev Org 가입 신청** — D-U-N-S/법인카드 준비.
3. **회사 도메인 메일 3개 분리 생성** — Apple/Google/Firebase 가입 사용.
4. **ADR-001 결재** — PO/CTO/Ops 서명.
5. **Sentry 조직 + 4 DSN 발급** — Sprint 1 BLOCKER #9 외부 작업.

가장 큰 critical path는 **Kakao 비즈앱 심사**. Apple/Google보다 우선 신청 권장.

---

## 현재 진행 상태 (참고)

### BE 완료 BLOCKER (2026-05-26 단일 세션 20 commits)
- #1 V65 Idempotency / #2 V66 JWT rotation BE / #3 V67 OAuth nonce / #4 Walk 트랜잭션 (이미 운영) / #5 V68 Chat 모더레이션 BE / #6 EXIF JPEG+PNG / #7 V69/V70 Admin / #8 FCM cleanup BE / #12 ETag

### 마이그레이션 V65-V70 dev 적용 완료
- V65 gold_idempotency_keys / V66 user_refresh_tokens / V67 oauth_nonces / V68 chat hidden_at / V69 admin TOTP replay / V70 audit userAgent

### 테스트 PASS
- 단위 19 (Idempotency 7 + JWT 7 + OAuth 5)
- 통합 7 (V65 Concurrency 2 + V66 Rotation 5)

### Jenkins 파이프라인 통과
- `01-GoldPet-API` ✅ dev API HTTP 200 UP
- `02-GoldPet-Frontend` ✅ Playwright OpenAPI contract test PASS

### 관련 문서
- `docs/GoldPet_상용출시_사전평가_v2.md` (v2.1 patched + v2.2 마이그레이션 시프트)
- `docs/GoldPet_상용출시_BLOCKER_Sprint_Plan.md` (Sprint 0-6 + retrospective)
- `docs/GoldPet_상용출시_회사계정전환_플랜.md` (만남스퀘어 법인 전환)
- `docs/corporate_account_progress_tracker.md` (Week 0-10, 58 항목 체크리스트)
- `docs/ADR-001-option-A-beta-launch.md` (옵션 A 결재서)
- `docs/runbook_sentry_setup.md` / `docs/runbook_grafana_launch_dashboard.md`
- `docs/product_roadmap_post_launch.md` (§3 백로그 P0-P3)

---

## 변경 이력
- 2026-05-26: 최초 작성 (BE 99% 완료 시점)

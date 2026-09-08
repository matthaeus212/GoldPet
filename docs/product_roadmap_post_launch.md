# GoldPet Post-Launch Product Roadmap

> 작성일: 2026-05-26
> 출처: `GoldPet_상용출시_사전평가_v2.md` §3 8개 백로그 이관
> 본 문서는 **출시 게이트와 무관한** retention/성장/차별화 백로그입니다.
> 출시 (옵션 A 베타) 이후 sprint 단위로 점진 도입합니다.

---

## 우선순위 매트릭스

| # | 제안 | 분류 | 기대 효과 | 우선순위 | 예상 sprint |
|---|---|---|---|---|---|
| 1 | 산책 코스 공유/평점 | Retention | D7 retention +5%p | **P0** | Post-launch Sprint 1 |
| 6 | 커뮤니티 best ranking + algorithm feed | Engagement | 콘텐츠 노출 효율화 | **P0** | Post-launch Sprint 1 |
| 2 | 펫 챌린지/뱃지 시스템 | Engagement | DAU 세션 시간 +15% | **P1** | Post-launch Sprint 2 |
| 3 | 산책 친구 매칭 v2 (location + 펫 성향) | Growth | 매칭 성공률 +20%p | **P1** | Post-launch Sprint 2 |
| 7 | 산책 SNS 공유 카드 | Growth | viral k>0.1 | **P1** | Post-launch Sprint 2 |
| 5 | Live Activity / Widget v2 | UX | 재진입율 향상 | **P2** | Post-launch Sprint 3 |
| 4 | AI 펫 건강 분석 | Differentiation | 프리미엄 USP | **P2** | Post-launch Sprint 3 |
| 8 | 골드 스토어 v2 + 파트너 제휴 | Monetization | ARPU 확보 | **P3** | 옵션 B 전환 후 (IAP 활성화) |

---

## 상세

### P0 — Post-launch Sprint 1 (출시 +2주~+4주)

#### 1. 산책 코스 공유/평점
- **근거**: 기존 `walk_course_platform` 백로그 (`docs/walk-course-platform.md` 참조).
- **MVP 범위**: admin 큐레이션 10개 코스 + 사용자 등록 form + 별점/리뷰.
- **How-to**:
  - 기존 `walk_courses` 테이블 + 별점/리뷰 컬럼 추가.
  - `AdminCourseService.curate()` + `CourseReviewService`.
  - FE: course detail page 별점 UI.
- **KPI**: 출시 1개월 내 사용자 등록 코스 ≥50건, 별점 평균 ≥4.0.

#### 6. 커뮤니티 best ranking + algorithm feed
- **근거**: `docs/best-ranking.md` (기존 백로그).
- **MVP 범위**: daily/weekly score 산출 (좋아요 + 댓글 + 조회 가중치) + best 탭 노출.
- **How-to**:
  - Quartz job daily 00:00 → `community_post_scores` 테이블 갱신.
  - React Query polling 또는 SSE로 best 탭 갱신.
- **KPI**: best 탭 진입율 ≥30%, 평균 체류 시간 +20%.

---

### P1 — Post-launch Sprint 2 (출시 +4주~+8주)

#### 2. 펫 챌린지/뱃지 시스템
- **근거**: 기존 `badge-mission-plan` 백로그.
- **MVP 범위**: 주간 챌린지 3개 (산책 거리/횟수/친구 만남) + 뱃지 9종.
- **How-to**:
  - 신규 마이그레이션 `badges`, `user_badges`, `weekly_challenges` 테이블.
  - `AdminBadgeService` + cron job.
- **KPI**: 주간 챌린지 참여율 ≥40%, 뱃지 1개 이상 보유 사용자 ≥60%.

#### 3. 산책 친구 매칭 v2 (location + 펫 성향)
- **근거**: 현재는 단순 거리 기반.
- **MVP 범위**: PostGIS `ST_DWithin` + Cosine similarity (pet personality vector).
- **How-to**:
  - `pets.personality_vector` (jsonb, 5차원) 추가.
  - 매칭 알고리즘: 거리 30% + 펫 성향 40% + 산책 시간대 30%.
- **KPI**: 매칭 후 친구 수락율 +20%p.

#### 7. 산책 SNS 공유 카드
- **근거**: `walk-share-feature` 백로그.
- **MVP 범위**: 산책 종료 화면 → 공유 카드 (OG meta + dynamic link) → 인스타그램/카카오톡 공유.
- **How-to**:
  - Branch.io 또는 Firebase Dynamic Links 도입.
  - utm 추적 + 가입 전환 시 추천인에게 골드.
- **KPI**: viral k coefficient ≥0.1 (1명 공유 → 0.1명 신규 가입).

---

### P2 — Post-launch Sprint 3 (출시 +8주~+12주)

#### 5. Live Activity (iOS) + Widget (Android)
- **근거**: iOS 16.1+ ActivityKit 활용, Android Glance API.
- **MVP 범위**: 산책 중 잠금화면에 거리/시간/골드 실시간 표시.
- **How-to**:
  - iOS: WalkActivityWidget Bundle (`com.mannam.goldpet.WalkActivityWidget`) 활용.
  - Android: `androidx.glance:glance-appwidget`.
- **KPI**: Live Activity 활성 사용자 ≥40% (iOS), Widget 추가 ≥20% (Android).

#### 4. AI 펫 건강 분석
- **근거**: 기존 `ai-poop-health-analysis` 백로그.
- **MVP 범위**: 산책 사진 EXIF + AI Vision 분석 → 월간 리포트 카드.
- **How-to**:
  - OpenAI Vision API (`AIGenerationClient` 기존 인터페이스 활용).
  - 월간 리포트 PDF 생성 + 마이페이지 노출.
- **KPI**: 리포트 열람율 ≥30%, 프리미엄 전환 lead +5%p.

---

### P3 — 옵션 B 전환 후 (IAP 활성화 시점)

#### 8. 골드 스토어 v2 + 파트너 제휴
- **근거**: 기존 `gold-store` 백로그 + Emoticon pilot.
- **MVP 범위**: 한정판 굿즈/이모티콘/펫 액세서리 + 파트너 제휴 쿠폰.
- **How-to**:
  - IAP/PG 연동 + 옵션 B 전환과 함께 launch.
  - V69+ migrations: `gold_store_items`, `purchase_orders`, `partner_coupons`.
- **KPI**: 월 GMV ≥1000만원, ARPU ≥3000원.

---

## 거버넌스

- 각 P-tier 진입 시 Product Owner 결재.
- KPI 미달 sprint는 다음 sprint에서 pivot 결정.
- 출시 후 retrospective는 sprint 단위 (4주 주기).

# ADR-001: 옵션 A (베타 IAP OFF) 우선 출시 결재서

> **작성일**: 2026-05-26
> **결재 대기 상태**: ⏳ Product Owner sign-off 필요
> **근거 문서**: `docs/GoldPet_상용출시_사전평가_v2.md` (v2.1 patched, Critic APPROVED)
> **실행 계획**: `docs/GoldPet_상용출시_BLOCKER_Sprint_Plan.md`

---

## 결재 의안

**골드펫 정식 상용 출시를 다음 조건으로 진행한다**:

1. **옵션 A** — 베타 출시 시점에 **IAP (인앱 결제) 비활성화**.
2. 골드는 **산책 보상 (reward) / 차감 (spend) 만 활성화**, **충전 (charge) 비활성화**.
3. BLOCKER 13건 해소 (4 sprint) → 14일 closed test 통과 → 정식 출시.
4. 옵션 B (IAP 활성화) 전환은 출시 후 1-2개월 안정화 확인 후 별도 결재.

---

## Decision Drivers

| # | 동인 | 근거 |
|---|---|---|
| 1 | 사용자 안전 | PII 노출 (EXIF GPS, Admin 침해) + UGC 모더레이션 부재 시 앱스토어 reject / 법적 리스크 |
| 2 | 금전 정합성 | 골드는 가상화폐 — 중복 적립/차감 1건이라도 발생 시 신뢰도 회복 불가 |
| 3 | 출시 일정 | 회사 계정 전환 (만남스퀘어 법인) 완료 후 14일 closed test 통과 시 정식 제출. 7월 출시 목표 유지 |

---

## Alternatives Considered

| 옵션 | 설명 | 채택 | 사유 |
|---|---|---|---|
| **A: 베타 우선 (IAP OFF)** | BLOCKER 13건 + 모더레이션 완료 후 출시 | ✅ **채택** | BLOCKER 표면적 최소화, 일정 단축, optimistic lock으로 충분 |
| **B: Plan-B Fallback** | A 출시 후 1-2개월 내 IAP 활성화 + Idempotency 인프라 추가 | ⏸️ **fallback 보류** | 옵션 A 안정화 확인 후 진행 |
| **C: 정식 즉시 (IAP ON)** | Idempotency + IAP + 모든 BLOCKER 동시 완료 후 제출 | ❌ **비채택** | 출시 지연 4-6주, BLOCKER 표면적 확대, 14일 closed test 회귀 비용 ↑ |

---

## Why Chosen (Option A)

1. **BLOCKER 표면적 최소화**: IAP/PG 연동을 미루면 결제 idempotency 클라이언트 변경 (4 프로젝트)이 불필요.
2. **출시 일정 단축**: 옵션 C 대비 2-3주 단축.
3. **데이터 정합성 충분 보장**: 골드 reward/spend만은 `@Version` optimistic lock으로 충분 (재시도 안전성 불요).
4. **점진적 위험 분산**: 옵션 B 전환 시 결제 인프라 도입을 별도 sprint로 분리하여 회귀 범위 축소.

---

## Consequences

### 긍정적
- 출시 일정 5-6주 유지 가능 (회사 계정 전환 트랙과 sync).
- 14일 closed test 회귀 범위 축소 → KPI 미달 가능성 낮음.
- 사용자 첫 인상은 "안정성" 중심으로 형성.

### 부정적
- **매출 발생 1-2개월 지연**: 옵션 B 전환 전까지 monetization 0.
- **IAP 도입 시 마이그레이션 1회 추가**: V63 Idempotency는 출시 시점에 도입 (V64-V68과 함께)되므로 옵션 B 전환은 IAP/PG 연동 + 골드 스토어 UI만 추가.
- **사용자 기대치 일부 미충족**: "충전 가능 앱" 기대 사용자 일부 이탈 가능 (마케팅 메시지로 완화).

### 위험 완화
- 골드 충전 UI는 "준비 중" 상태로 노출 + waiting list 등록 → 옵션 B 전환 시 즉시 안내.
- 산책 보상 골드 양을 출시 시점에 +20% 조정 (충전 없이도 사용처 채울 양 확보).

---

## Follow-ups

1. **Sprint Plan 실행** (`GoldPet_상용출시_BLOCKER_Sprint_Plan.md`).
2. **회사 계정 전환 트랙 가동** (`GoldPet_상용출시_회사계정전환_플랜.md` Week 0).
3. **post-launch 백로그 분리** (`product_roadmap_post_launch.md` — 작성 완료).
4. **옵션 B 전환 결재** — 출시 +6주 시점에 KPI 데이터 기반 별도 ADR 작성.

---

## 결재란

| 역할 | 이름 | 결재일 | 서명 |
|---|---|---|---|
| Product Owner | _________ | YYYY-MM-DD | ⏳ |
| CTO / Tech Lead | _________ | YYYY-MM-DD | ⏳ |
| Ops Lead | _________ | YYYY-MM-DD | ⏳ |

**결재 완료 후 다음 액션**: Sprint 0 나머지 항목 즉시 가동 (Sentry DSN 발급, 회사 계정 가입 신청).

---

## 변경 이력
- 2026-05-26: 최초 작성, ralplan 합의 v2.1 기반.

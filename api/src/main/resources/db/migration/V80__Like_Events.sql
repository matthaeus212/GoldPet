-- V80: like_events 추세 추가형 로그 (W1(2), plan §1 — match-rate 퍼널 정확도).
-- likes 테이블은 MUTABLE(relike/cancel 가 같은 행 status 를 뒤집음) → 퍼널을 likes 에서 파생하면 안 됨.
-- LikeService.likeUser / unlikeUser(그리고 MatchService.likeUser 두 번째 seam) 시점에 1행씩 append-only 로 기록.
-- is_match = 그 시점에 THIS 좋아요가 매치를 만들었는지(point-in-time 사실) — "현재 매치 상태"로 읽으면 안 됨.
-- cohort = experiment_assignment 에서 READ(ExperimentService.readCohortOrNone) — 미할당(미노출) 유저는 NONE(A/B 분모 제외).
--          해시 재계산 금지(guardrail #3) — V79 의 단일 출처 행에서만 읽는다.
-- 행동/실험 데이터 → user_id ON DELETE CASCADE (privacy).

CREATE TABLE IF NOT EXISTS like_events (
    id              BIGSERIAL   PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_user_id  BIGINT      NOT NULL,
    action          VARCHAR(16) NOT NULL CHECK (action IN ('LIKE', 'CANCEL')),
    is_match        BOOLEAN     NOT NULL,
    cohort          VARCHAR(16) NOT NULL CHECK (cohort IN ('TREATMENT', 'CONTROL', 'NONE')),
    source          VARCHAR(32),
    occurred_at     TIMESTAMP   NOT NULL DEFAULT now()
);

-- window 집계(match-rate 퍼널, W2) + 코호트별 slice 가속.
CREATE INDEX IF NOT EXISTS idx_like_events_occurred_at ON like_events (occurred_at);
CREATE INDEX IF NOT EXISTS idx_like_events_cohort      ON like_events (cohort);

COMMENT ON TABLE  like_events                IS 'append-only 좋아요/취소 이벤트 로그 — match-rate 퍼널 사실(V80, W1). likes 테이블(MUTABLE)에서 파생 금지';
COMMENT ON COLUMN like_events.action         IS 'LIKE | CANCEL — 좋아요/취소 시점에 1행 append';
COMMENT ON COLUMN like_events.is_match       IS 'point-in-time: THIS 좋아요가 그 순간 매치를 만들었는지. "현재 매치 상태"로 읽지 말 것';
COMMENT ON COLUMN like_events.cohort         IS 'TREATMENT | CONTROL | NONE — experiment_assignment 에서 READ(재계산 금지). NONE=미노출(A/B 분모 제외)';
COMMENT ON COLUMN like_events.source         IS '좋아요 발생 리스트/정렬 출처(compatible|distance|popular|received|home|registered 등). nullable';

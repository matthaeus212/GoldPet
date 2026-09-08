-- Gold Sink: 프로필 부스트 (W2 동반 — 미션/스트릭 골드 소비처, 인플레 완화 밸브).
-- 부스트는 고정 윈도우 동안 친구 탐색에서 노출/랭크를 일시 상승. 골드는 GoldService.spendGold()로 차감.
-- 비용/지속시간은 SystemSetting(profile.boost.cost / profile.boost.duration_minutes)로 외부화.

CREATE TABLE profile_boosts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    started_at TIMESTAMP   NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    gold_cost  INT         NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

-- 활성 부스트 조회(user_id + 미만료) 가속.
CREATE INDEX idx_profile_boosts_user_expires ON profile_boosts (user_id, expires_at);

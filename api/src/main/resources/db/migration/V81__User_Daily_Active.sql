-- V81: user_daily_active — true app-open DAU signal.
-- last_login_at is a single overwritten timestamp (no history), so it cannot
-- support retention/DAU analysis. This append-only (user_id, active_date) table fixes that.
-- One row per user per KST day; written idempotently via INSERT ... ON CONFLICT DO NOTHING.
CREATE TABLE IF NOT EXISTS user_daily_active (
    user_id     BIGINT      NOT NULL,
    active_date DATE        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, active_date),
    CONSTRAINT fk_user_daily_active_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- DAU aggregations (T4 metrics) scan by date; index supports COUNT(DISTINCT user_id) per day/range.
CREATE INDEX IF NOT EXISTS idx_user_daily_active_date ON user_daily_active (active_date);

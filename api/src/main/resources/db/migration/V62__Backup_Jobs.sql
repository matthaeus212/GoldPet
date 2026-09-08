-- Phase 3 백업 작업 큐: 수동 trigger UI + 자동 systemd timer 양쪽이 INSERT, runner 가 PENDING pickup
CREATE TABLE backup_jobs (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL,  -- PENDING, RUNNING, SUCCEEDED, FAILED
    triggered_by_admin_id BIGINT REFERENCES admin_users(id),  -- nullable: cron 자동 = NULL
    trigger_type VARCHAR(20) NOT NULL,  -- AUTO_CRON, MANUAL
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    file_path TEXT,
    file_size_bytes BIGINT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_backup_jobs_status ON backup_jobs(status, created_at DESC);

-- V69: Admin TOTP replay 보호 — Sprint 2 BLOCKER #7 (2FA 보강).
-- 사전 상태:
--   AdminTotpService (generateSecret/getQrCodeUrl/validateCode) — googleauth 1.5.0 기반 — 이미 운영 중.
--   AdminAuthService.setup2fa / confirm2fa / verify2fa / cancel2fa — 이미 운영 중.
--   admin_users.otp_secret 컬럼에 TOTP shared secret 평문 저장.
-- 본 마이그레이션은 verify2fa replay 보호를 위한 last_otp_used_at 컬럼만 보강 (non-destructive ALTER).
-- POST-LAUNCH: otp_secret 컬럼 암호화 (EncryptionConverter 적용 + 기존 데이터 reencryption) 은 별도 sprint
--   (현재는 admin 인원 매우 적고 DB 접근 통제 강하여 우선순위 낮음).

ALTER TABLE admin_users ADD COLUMN IF NOT EXISTS last_otp_used_at TIMESTAMP;

COMMENT ON COLUMN admin_users.last_otp_used_at IS 'TOTP 마지막 성공 verify 시점 — 동일 30s window 코드 재사용 차단 (V69)';

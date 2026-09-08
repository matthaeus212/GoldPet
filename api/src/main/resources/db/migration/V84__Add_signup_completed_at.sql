-- V84: signup_completed_at — single onboarding-completion criterion, decoupled from PII
-- fields (name/nickname/phone). See
-- docs/superpowers/specs/2026-06-16-signup-completed-onboarding-flag-design.md
ALTER TABLE users ADD COLUMN signup_completed_at TIMESTAMP NULL;

-- Conservative backfill: preserve existing-account login (never lock out a real user),
-- but never mark a nickname-only social first-touch row as complete.
-- name/phone are AES-encrypted; we only null-check the ciphertext column (never compare values).
UPDATE users
SET signup_completed_at = COALESCE(created_at, updated_at, CURRENT_TIMESTAMP)
WHERE oauth_provider = 'LOCAL'   -- LOCAL members: complete by definition
   OR password IS NOT NULL       -- belt-and-suspenders for LOCAL
   OR name IS NOT NULL           -- social who finished snsSignup (name set there)
   OR username IS NOT NULL;      -- preserve any legacy username-bearing rows

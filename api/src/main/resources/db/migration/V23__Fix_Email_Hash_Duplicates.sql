-- Fix duplicate users caused by findByEmailHash converter bug
-- Soft-delete duplicates (keep the one with highest id per email_hash)
UPDATE users
SET status = 'WITHDRAWN',
    email_hash = email_hash || '-dup-' || id,
    oauth_id = oauth_id || '-dup-' || id,
    is_active = false
WHERE id NOT IN (
    SELECT MAX(id) FROM users
    WHERE email_hash IS NOT NULL
    GROUP BY email_hash
)
AND email_hash IN (
    SELECT email_hash FROM users
    WHERE email_hash IS NOT NULL
    GROUP BY email_hash
    HAVING COUNT(*) > 1
);

-- Add UNIQUE constraint (now safe - no duplicate email_hash values remain)
ALTER TABLE users ADD CONSTRAINT uq_users_email_hash UNIQUE (email_hash);

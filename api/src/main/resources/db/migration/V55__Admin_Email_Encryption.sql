-- admin_users PII 암호화: email/name을 AES-256-GCM으로 암호화하고 email_hash(BlindIndex)로 로그인 조회
-- users 테이블 패턴(V16/V23)과 동일한 구조.

-- 1) email의 unique 제약 제거: random-IV 암호화는 동일 평문도 매번 다른 ciphertext를 만들어
--    SQL 레벨 equality/unique가 의미를 잃음. 조회는 email_hash로.
ALTER TABLE admin_users DROP CONSTRAINT IF EXISTS admin_users_email_key;

-- 2) email_hash 컬럼 추가 (초기엔 nullable — 데이터 마이그레이션 후 채움)
ALTER TABLE admin_users ADD COLUMN IF NOT EXISTS email_hash VARCHAR(255);

-- 3) 로그인 조회용 인덱스 + uniqueness (Postgres의 unique index는 NULL 다중 허용이라 초기 NULL 상태에서도 안전)
CREATE UNIQUE INDEX IF NOT EXISTS uq_admin_users_email_hash ON admin_users(email_hash);

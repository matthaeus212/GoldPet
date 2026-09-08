-- V13: birth_date 컬럼을 DATE → VARCHAR(255)로 변경 (AES-256-GCM 암호화 저장용)
-- 기존 날짜 값은 'YYYY-MM-DD' 문자열로 변환됨, 이후 마이그레이션 API로 암호화 실행
ALTER TABLE users ALTER COLUMN birth_date TYPE VARCHAR(255) USING birth_date::TEXT;

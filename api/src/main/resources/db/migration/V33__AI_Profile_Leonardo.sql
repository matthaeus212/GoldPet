-- V33: Leonardo AI 프로필 생성 기능 인프라
-- 1. ai_profile_requests 테이블에 Leonardo 관련 컬럼 추가
-- 2. ai_styles 테이블에 Leonardo 스타일 프리셋 seed 데이터

-- Leonardo AI 요청 추적 컬럼 추가
ALTER TABLE ai_profile_requests
    ADD COLUMN IF NOT EXISTS leonardo_generation_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS leonardo_image_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS daily_limit_date DATE;

-- 인덱스: 일일 생성 한도 체크용 (user_id + created_at DATE)
CREATE INDEX IF NOT EXISTS idx_ai_profile_requests_user_date
    ON ai_profile_requests (user_id, DATE(created_at));

-- 인덱스: Leonardo generation ID 조회
CREATE INDEX IF NOT EXISTS idx_ai_profile_requests_leonardo_gen_id
    ON ai_profile_requests (leonardo_generation_id)
    WHERE leonardo_generation_id IS NOT NULL;

-- ai_styles 테이블에 Leonardo preset_id 컬럼 추가 (Leonardo 스타일 preset 매핑)
ALTER TABLE ai_styles
    ADD COLUMN IF NOT EXISTS preset_id VARCHAR(100);

-- 기존 ai_styles 레코드 비활성화 후 Leonardo 기반 스타일 seed
-- (기존 데이터 없으면 무시됨)
UPDATE ai_styles SET is_active = false WHERE is_active = true;

-- Leonardo 스타일 옵션 seed
INSERT INTO ai_styles (id, name, description, preview_url, gold_cost, is_active, display_order, preset_id, created_at, updated_at)
VALUES
    ('LEONARDO_ILLUSTRATION', '일러스트레이션', '따뜻하고 귀여운 동화 일러스트 스타일', null, 10, true, 1, 'ILLUSTRATION', NOW(), NOW()),
    ('LEONARDO_ANIME', '애니메이션', '밝고 생동감 넘치는 애니메이션 스타일', null, 10, true, 2, 'ANIME_GENERAL', NOW(), NOW()),
    ('LEONARDO_PHOTOGRAPHY', '사진 리터칭', '자연스럽고 선명한 사진 보정 스타일', null, 10, true, 3, 'PHOTOGRAPHY', NOW(), NOW()),
    ('LEONARDO_CINEMATIC', '시네마틱', '영화 같은 드라마틱한 분위기', null, 15, true, 4, 'CINEMATIC', NOW(), NOW()),
    ('LEONARDO_WATERCOLOR', '수채화', '부드럽고 감성적인 수채화 스타일', null, 10, true, 5, 'WATERCOLOR', NOW(), NOW()),
    ('LEONARDO_OIL_PAINTING', '유화', '고전적이고 고급스러운 유화 스타일', null, 15, true, 6, 'OIL_PAINTING', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    gold_cost = EXCLUDED.gold_cost,
    is_active = EXCLUDED.is_active,
    display_order = EXCLUDED.display_order,
    preset_id = EXCLUDED.preset_id,
    updated_at = NOW();

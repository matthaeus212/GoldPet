-- V34: 커스텀 모드 AI 프로필 생성 시 pet_id nullable 허용
ALTER TABLE ai_profile_requests ALTER COLUMN pet_id DROP NOT NULL;

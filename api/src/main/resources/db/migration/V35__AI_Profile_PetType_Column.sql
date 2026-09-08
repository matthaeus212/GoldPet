-- V35: 커스텀 모드 AI 프로필 생성 시 petType 저장용 컬럼 추가
ALTER TABLE ai_profile_requests ADD COLUMN pet_type VARCHAR(20);

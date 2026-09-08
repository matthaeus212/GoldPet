-- 회원가입 축하 골드(SIGNUP_WELCOME) 중복 지급 방지 DB 레벨 unique index
-- 배경: GoldService.grantWelcomeGoldIfNeeded()가 서비스 레벨 existsByUserIdAndReferenceType 체크에만
-- 의존 → 동시 요청/멀티 인스턴스 레이스 컨디션에서 중복 지급이 가능. DB unique 제약으로 방어.
-- 사전 확인: 2026-07-09 운영 DB에 SIGNUP_WELCOME 중복 레코드 없음 검증됨.
-- 만약 기존 데이터에 중복이 있다면 이 마이그레이션은 실패하므로, 배포 전 아래 쿼리로 재확인할 것:
--   SELECT user_id, COUNT(*) FROM gold_transactions WHERE reference_type = 'SIGNUP_WELCOME' GROUP BY user_id HAVING COUNT(*) > 1;
CREATE UNIQUE INDEX IF NOT EXISTS uq_signup_welcome_gold_per_user
    ON gold_transactions (user_id, reference_type)
    WHERE reference_type = 'SIGNUP_WELCOME';

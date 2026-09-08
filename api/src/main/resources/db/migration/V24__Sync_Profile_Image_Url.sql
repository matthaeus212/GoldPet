-- V24: user_profile_images가 있는 유저의 profile_image_url을 첫 번째 이미지로 동기화
-- syncUserFields()가 OAuth 아바타로 덮어쓴 기존 데이터 복구
UPDATE users u
SET profile_image_url = upi.image_url
FROM user_profile_images upi
WHERE upi.user_id = u.id
  AND upi.order_index = 0
  AND u.id IN (SELECT DISTINCT user_id FROM user_profile_images);

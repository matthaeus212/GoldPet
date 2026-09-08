-- Backfill user_profile_images for OAuth-only users who have profileImageUrl but no gallery rows
INSERT INTO user_profile_images (user_id, image_url, order_index, created_at, updated_at)
SELECT id, profile_image_url, 0, NOW(), NOW()
FROM users
WHERE profile_image_url IS NOT NULL
  AND id NOT IN (SELECT DISTINCT user_id FROM user_profile_images);

-- Backfill pet_profile_images for pets with profileImageUrl but no gallery rows
INSERT INTO pet_profile_images (pet_id, image_url, order_index, created_at, updated_at)
SELECT id, profile_image_url, 0, NOW(), NOW()
FROM pets
WHERE profile_image_url IS NOT NULL
  AND id NOT IN (SELECT DISTINCT pet_id FROM pet_profile_images);

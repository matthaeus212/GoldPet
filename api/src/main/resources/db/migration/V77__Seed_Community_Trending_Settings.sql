-- 커뮤니티 트렌딩(인기) 랭킹 파라미터 — 어드민에서 재배포 없이 튜닝.
-- score = (like*w_like + view*w_view + comment*w_comment) / (ageHours + 2)^gravity, 최근 window_days 만 후보.
INSERT INTO system_settings (setting_key, setting_value, description, created_at, updated_at) VALUES
  ('community.trending.w_like',      '3',  '트렌딩 가중치 — 좋아요', now(), now()),
  ('community.trending.w_comment',   '5',  '트렌딩 가중치 — 댓글', now(), now()),
  ('community.trending.w_view',      '1',  '트렌딩 가중치 — 조회', now(), now()),
  ('community.trending.gravity',     '1.5','트렌딩 시간감쇠 지수 (클수록 최신글 우대)', now(), now()),
  ('community.trending.window_days', '14', '트렌딩 후보 기간(일) — 이 기간 이후 글만 랭킹', now(), now())
ON CONFLICT (setting_key) DO NOTHING;

-- 홈 친구추천 v2(반경무제한·이성우선·신규완화·거리순) 런타임 스위치 시드. 기본 OFF — QA 통과 후 admin/API 로 ON.
-- 멱등: 이미 존재하는 키(예: dev 에서 수동 ON)는 보존.
INSERT INTO system_settings (setting_key, setting_value, description, created_at, updated_at) VALUES
  ('reco.recommendation_v2.enabled', 'false', '홈 친구추천 v2 사용 (반경무제한·이성우선·신규완화·거리순). OFF 시 기존 10km 거리순 폴백', now(), now())
ON CONFLICT (setting_key) DO NOTHING;

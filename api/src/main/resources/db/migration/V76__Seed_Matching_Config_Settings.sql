-- W1 궁합 스코어링 설정을 어드민에서 관리 가능하도록 system_settings 에 시드.
-- 멱등: 이미 존재하는 키(예: dev 에서 수동 ON 한 match.compatibility.enabled)는 보존.
INSERT INTO system_settings (setting_key, setting_value, description, created_at, updated_at) VALUES
  ('match.compatibility.enabled', 'false', '궁합순 정렬 사용 (OFF 시 거리순으로 폴백)', now(), now()),
  ('match.score.w_distance',      '0.40',  '궁합 점수 가중치 — 거리 근접도 (0~1)', now(), now()),
  ('match.score.w_interest',      '0.25',  '궁합 점수 가중치 — 관심사 일치 (0~1)', now(), now()),
  ('match.score.w_hobby',         '0.20',  '궁합 점수 가중치 — 취미 일치 (0~1)', now(), now()),
  ('match.score.w_temperament',   '0.15',  '궁합 점수 가중치 — 반려동물 기질 일치 (0~1)', now(), now()),
  ('profile.boost.rank_bonus',    '0.15',  '프로필 부스트 구매자 궁합 점수 가산점 (0~1, 최종 클램프)', now(), now())
ON CONFLICT (setting_key) DO NOTHING;

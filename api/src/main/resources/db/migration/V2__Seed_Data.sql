-- =================================================================================================
-- 1. Pet Species & Breeds
-- =================================================================================================

INSERT INTO pet_species (code, name, description) VALUES
('DOG', '강아지', '멍멍이'),
('CAT', '고양이', '야옹이');

-- Dogs
INSERT INTO pet_breeds (species_id, name) VALUES
((SELECT id FROM pet_species WHERE code = 'DOG'), '골든 리트리버'),
((SELECT id FROM pet_species WHERE code = 'DOG'), '말티즈'),
((SELECT id FROM pet_species WHERE code = 'DOG'), '비숑 프리제'),
((SELECT id FROM pet_species WHERE code = 'DOG'), '포메라니안'),
((SELECT id FROM pet_species WHERE code = 'DOG'), '시바견'),
((SELECT id FROM pet_species WHERE code = 'DOG'), '웰시 코기'),
((SELECT id FROM pet_species WHERE code = 'DOG'), '치와와'),
((SELECT id FROM pet_species WHERE code = 'DOG'), '시츄'),
((SELECT id FROM pet_species WHERE code = 'DOG'), '진돗개'),
((SELECT id FROM pet_species WHERE code = 'DOG'), '믹스견');

-- Cats
INSERT INTO pet_breeds (species_id, name) VALUES
((SELECT id FROM pet_species WHERE code = 'CAT'), '코리안 숏헤어'),
((SELECT id FROM pet_species WHERE code = 'CAT'), '러시안 블루'),
((SELECT id FROM pet_species WHERE code = 'CAT'), '페르시안'),
((SELECT id FROM pet_species WHERE code = 'CAT'), '샴'),
((SELECT id FROM pet_species WHERE code = 'CAT'), '터키시 앙고라'),
((SELECT id FROM pet_species WHERE code = 'CAT'), '스꼬티쉬 폴드'),
((SELECT id FROM pet_species WHERE code = 'CAT'), '노르웨이 숲'),
((SELECT id FROM pet_species WHERE code = 'CAT'), '랙돌'),
((SELECT id FROM pet_species WHERE code = 'CAT'), '먼치킨'),
((SELECT id FROM pet_species WHERE code = 'CAT'), '믹스묘');

-- =================================================================================================
-- 2. Community Categories
-- =================================================================================================

INSERT INTO community_categories (code, name) VALUES
('WALK', '산책 후기'),
('HEALTH', '건강 상담'),
('INFO', '정보 공유'),
('QNA', '질문과 답변'),
('SHOWOFF', '우리 아이 자랑'),
('MEETUP', '산책 친구 구해요');

-- =================================================================================================
-- 3. Badges (Gamification)
-- =================================================================================================

INSERT INTO badges (code, name, description, image_url) VALUES
('FIRST_WALK', '첫 걸음', '생애 첫 산책을 기록했어요!', 'https://cdn.goldpet.com/badges/first_walk.png'),
('WALKER_10KM', '동네 한 바퀴', '누적 산책 거리 10km 달성', 'https://cdn.goldpet.com/badges/walker_10km.png'),
('WALKER_100KM', '마라토너', '누적 산책 거리 100km 달성', 'https://cdn.goldpet.com/badges/walker_100km.png'),
('SOCIAL_BUTTERFLY', '핵인싸', '친구 매칭 5회 달성', 'https://cdn.goldpet.com/badges/social_5.png'),
('EARLY_BIRD', '아침형 펫', '아침 6-9시 사이에 5회 산책', 'https://cdn.goldpet.com/badges/early_bird.png'),
('NIGHT_OWL', '올빼미', '밤 10시 이후에 5회 산책', 'https://cdn.goldpet.com/badges/night_owl.png'),
('COLLECTOR', '다 모았개', '뱃지 5개 획득', 'https://cdn.goldpet.com/badges/collector.png');

-- =================================================================================================
-- 4. Initial Places (Sample Data)
-- =================================================================================================

-- Seoul Forest
INSERT INTO places (name, category, description, address, location_geom) VALUES
('서울숲', 'PARK', '반려견과 산책하기 좋은 대형 공원', '서울 성동구 뚝섬로 273', ST_SetSRID(ST_MakePoint(127.037, 37.544), 4326));

-- Yeouido Hangang Park
INSERT INTO places (name, category, description, address, location_geom) VALUES
('여의도 한강공원', 'PARK', '탁 트인 한강을 보며 산책하세요', '서울 영등포구 여의동로 330', ST_SetSRID(ST_MakePoint(126.934, 37.528), 4326));

-- Dog Cafe Example
INSERT INTO places (name, category, description, address, location_geom) VALUES
('멍멍 카페 강남점', 'CAFE', '넓은 놀이터가 있는 애견 카페', '서울 강남구 테헤란로 123', ST_SetSRID(ST_MakePoint(127.027, 37.497), 4326));

-- Animal Hospital Example
INSERT INTO places (name, category, description, address, location_geom) VALUES
('24시 튼튼 동물병원', 'HOSPITAL', '응급 진료 가능한 동물병원', '서울 서초구 서초대로 456', ST_SetSRID(ST_MakePoint(127.008, 37.485), 4326));

-- =================================================================================================
-- 5. Emoticon Packs
-- =================================================================================================

INSERT INTO emoticon_packs (name, description, price_gold) VALUES
('기본 강아지 팩', '귀여운 강아지 기본 이모티콘', 0),
('기본 고양이 팩', '시크한 고양이 기본 이모티콘', 0);

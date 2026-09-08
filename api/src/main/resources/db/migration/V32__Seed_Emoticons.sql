-- Seed 8 emoticons into existing packs from V2__Seed_Data.sql
-- Using subselect for pack_id to avoid hardcoded ID assumptions
-- image_url uses frontend public asset paths (local dev); replace with S3 URLs for production
INSERT INTO emoticons (pack_id, name, code, image_url, sort_order, is_active) VALUES
((SELECT id FROM emoticon_packs WHERE name = '기본 강아지 팩'), '강아지 웃음', 'dog_smile', '/assets/images/emoticons/default/dog_smile.png', 1, true),
((SELECT id FROM emoticon_packs WHERE name = '기본 강아지 팩'), '강아지 슬픔', 'dog_sad', '/assets/images/emoticons/default/dog_sad.png', 2, true),
((SELECT id FROM emoticon_packs WHERE name = '기본 강아지 팩'), '강아지 실망', 'dog_disappointed', '/assets/images/emoticons/default/dog_disappointed.png', 3, true),
((SELECT id FROM emoticon_packs WHERE name = '기본 강아지 팩'), '강아지 화남', 'dog_angry', '/assets/images/emoticons/default/dog_angry.png', 4, true),
((SELECT id FROM emoticon_packs WHERE name = '기본 고양이 팩'), '고양이 웃음', 'cat_smile', '/assets/images/emoticons/default/cat_smile.png', 5, true),
((SELECT id FROM emoticon_packs WHERE name = '기본 고양이 팩'), '고양이 슬픔', 'cat_sad', '/assets/images/emoticons/default/cat_sad.png', 6, true),
((SELECT id FROM emoticon_packs WHERE name = '기본 고양이 팩'), '고양이 행복', 'cat_happy', '/assets/images/emoticons/default/cat_happy.png', 7, true),
((SELECT id FROM emoticon_packs WHERE name = '기본 고양이 팩'), '고양이 화남', 'cat_angry', '/assets/images/emoticons/default/cat_angry.png', 8, true);

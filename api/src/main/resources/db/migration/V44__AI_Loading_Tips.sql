CREATE TABLE ai_loading_tips (
    id SERIAL PRIMARY KEY,
    content VARCHAR(200) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed existing hardcoded tips
INSERT INTO ai_loading_tips (content, display_order) VALUES
('강아지는 사람의 감정을 읽을 수 있어요 🐶', 1),
('고양이는 하루 16시간 이상 잠을 자요 😸', 2),
('반려동물과 산책하면 스트레스가 줄어들어요 🌿', 3),
('AI가 최고의 결과를 만들고 있어요 ✨', 4),
('강아지의 코 무늬는 사람의 지문처럼 고유해요 🐾', 5),
('고양이는 약 100가지 소리를 낼 수 있어요 🎵', 6),
('반려동물은 주인의 목소리를 기억해요 💕', 7);

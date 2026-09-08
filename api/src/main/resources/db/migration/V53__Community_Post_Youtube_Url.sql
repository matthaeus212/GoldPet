ALTER TABLE community_posts ADD COLUMN youtube_url VARCHAR(500) NULL;
COMMENT ON COLUMN community_posts.youtube_url IS 'Canonical YouTube embed URL (https://www.youtube-nocookie.com/embed/{videoId})';

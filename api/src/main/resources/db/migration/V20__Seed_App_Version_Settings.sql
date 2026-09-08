-- Seed app version settings for admin configuration
INSERT INTO system_settings (setting_key, setting_value, description, created_at, updated_at) VALUES
    ('app.version.android.minimum', '1.0.0', 'Android 최소 필수 버전 (이하 강제 업데이트)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('app.version.android.latest', '1.0.0', 'Android 최신 버전 (이하 권장 업데이트)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('app.version.ios.minimum', '1.0.0', 'iOS 최소 필수 버전 (이하 강제 업데이트)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('app.version.ios.latest', '1.0.0', 'iOS 최신 버전 (이하 권장 업데이트)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('app.version.update_url.android', '', 'Android 업데이트 URL (Play Store)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('app.version.update_url.ios', '', 'iOS 업데이트 URL (App Store)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('auth.link_suggestion.enabled', 'false', 'OAuth 계정 연동 제안 기능 활성화 여부', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (setting_key) DO NOTHING;

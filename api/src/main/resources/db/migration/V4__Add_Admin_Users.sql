-- Initial Super Admin (password: admin123)
INSERT INTO admin_users (email, password_hash, name, role, is_active, created_at, updated_at)
VALUES (
    'admin@goldpet.com',
    '$2a$10$jxeCyf8HGIIinLTeF2dXueRJg9W5sDjAnwquEuSw5guigFDiGfB42',
    '최고관리자',
    'SUPER_ADMIN',
    true,
    NOW(),
    NOW()
);

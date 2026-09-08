-- Gold Products Table
CREATE TABLE IF NOT EXISTS gold_products (
    id BIGSERIAL PRIMARY KEY,
    product_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    gold_amount INT NOT NULL,
    price INT NOT NULL,
    bonus INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed default products
INSERT INTO gold_products (product_code, name, gold_amount, price, bonus, display_order) VALUES
    ('gold_10', '골드 10개', 10, 1000, 0, 1),
    ('gold_50', '골드 50개', 50, 4500, 5, 2),
    ('gold_100', '골드 100개', 100, 8900, 10, 3),
    ('gold_300', '골드 300개', 300, 25000, 50, 4),
    ('gold_500', '골드 500개', 500, 40000, 100, 5);

-- Add original_transaction_id column for refund tracking
ALTER TABLE gold_transactions ADD COLUMN IF NOT EXISTS original_transaction_id BIGINT REFERENCES gold_transactions(id);

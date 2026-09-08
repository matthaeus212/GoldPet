ALTER TABLE gold_products ADD COLUMN product_type VARCHAR(20) NOT NULL DEFAULT 'ONE_TIME';
ALTER TABLE gold_products ADD COLUMN duration_months INT;
ALTER TABLE gold_products ADD COLUMN discount_percent INT DEFAULT 0;
ALTER TABLE gold_products ADD COLUMN monthly_price INT;

-- Deactivate old V14 seed products
UPDATE gold_products SET is_active = false WHERE product_code LIKE 'gold_%';

-- Seed subscription products
INSERT INTO gold_products (product_code, name, gold_amount, price, bonus, is_active, display_order, product_type, duration_months, discount_percent, monthly_price)
VALUES
  ('SUB_1M', '1개월', 0, 12000, 0, true, 1, 'SUBSCRIPTION', 1, 0, 12000),
  ('SUB_3M', '3개월', 0, 32400, 0, true, 2, 'SUBSCRIPTION', 3, 10, 10800),
  ('SUB_6M', '6개월', 0, 57600, 0, true, 3, 'SUBSCRIPTION', 6, 20, 9600),
  ('SUB_12M', '12개월', 0, 100800, 0, true, 4, 'SUBSCRIPTION', 12, 30, 8400);

-- Seed one-time products
INSERT INTO gold_products (product_code, name, gold_amount, price, bonus, is_active, display_order, product_type, duration_months, discount_percent, monthly_price)
VALUES
  ('ONE_10K', '10,000골드', 10000, 10000, 0, true, 10, 'ONE_TIME', NULL, 0, NULL),
  ('ONE_20K', '20,000골드', 20000, 18000, 0, true, 11, 'ONE_TIME', NULL, 10, NULL);

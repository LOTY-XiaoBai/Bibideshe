-- 广告可关联一件商品，也可关联有在售商品的商家主页。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='merchant' AND column_name='cover_url'), 'SELECT 1', 'ALTER TABLE merchant ADD COLUMN cover_url VARCHAR(255) NOT NULL DEFAULT ''''');
PREPARE storefront_stmt FROM @ddl;
EXECUTE storefront_stmt;
DEALLOCATE PREPARE storefront_stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='merchant_campaign' AND column_name='target_type'), 'SELECT 1', 'ALTER TABLE merchant_campaign ADD COLUMN target_type VARCHAR(16) NOT NULL DEFAULT ''PRODUCT''');
PREPARE storefront_stmt FROM @ddl;
EXECUTE storefront_stmt;
DEALLOCATE PREPARE storefront_stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='merchant_campaign' AND column_name='product_id' AND is_nullable='YES'), 'SELECT 1', 'ALTER TABLE merchant_campaign MODIFY COLUMN product_id BIGINT NULL');
PREPARE storefront_stmt FROM @ddl;
EXECUTE storefront_stmt;
DEALLOCATE PREPARE storefront_stmt;

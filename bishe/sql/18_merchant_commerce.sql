-- 商家邀请、商品审核和独立广告。保留旧商品及订单，不把旧广告自动当作已审核广告。
CREATE TABLE IF NOT EXISTS merchant (
  merchant_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  account_code CHAR(16) NOT NULL,
  password VARCHAR(100) NOT NULL,
  name VARCHAR(80) NOT NULL DEFAULT '',
  city VARCHAR(80) NOT NULL DEFAULT '',
  must_change_password TINYINT NOT NULL DEFAULT 1,
  auth_version INT NOT NULL DEFAULT 0,
  created_by VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME NULL,
  activated_at DATETIME NULL,
  UNIQUE KEY uk_merchant_account(account_code),
  KEY idx_merchant_expiry(activated_at,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_campaign (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  image_url VARCHAR(255) NOT NULL,
  review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  review_reason VARCHAR(500) NOT NULL DEFAULT '',
  revision INT NOT NULL DEFAULT 1,
  reviewed_by VARCHAR(20) NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_campaign_owner(merchant_id,id),
  KEY idx_campaign_review(review_status,id),
  KEY idx_campaign_product(product_id,review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_review_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  resource_type VARCHAR(16) NOT NULL,
  resource_id BIGINT NOT NULL,
  revision INT NOT NULL,
  decision VARCHAR(16) NOT NULL,
  reason VARCHAR(500) NOT NULL DEFAULT '',
  admin_id VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_review_resource(resource_type,resource_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='advertisement' AND column_name='merchant_id'), 'SELECT 1', 'ALTER TABLE advertisement ADD COLUMN merchant_id BIGINT NULL');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='advertisement' AND column_name='review_status'), 'SELECT 1', 'ALTER TABLE advertisement ADD COLUMN review_status VARCHAR(16) NOT NULL DEFAULT ''LEGACY''');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='advertisement' AND column_name='review_reason'), 'SELECT 1', 'ALTER TABLE advertisement ADD COLUMN review_reason VARCHAR(500) NOT NULL DEFAULT ''''');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='advertisement' AND column_name='revision'), 'SELECT 1', 'ALTER TABLE advertisement ADD COLUMN revision INT NOT NULL DEFAULT 1');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='advertisement' AND column_name='reviewed_by'), 'SELECT 1', 'ALTER TABLE advertisement ADD COLUMN reviewed_by VARCHAR(20) NULL');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='advertisement' AND column_name='reviewed_at'), 'SELECT 1', 'ALTER TABLE advertisement ADD COLUMN reviewed_at DATETIME NULL');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='order_record' AND column_name='merchant_id'), 'SELECT 1', 'ALTER TABLE order_record ADD COLUMN merchant_id BIGINT NULL');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='order_record' AND column_name='product_title'), 'SELECT 1', 'ALTER TABLE order_record ADD COLUMN product_title VARCHAR(100) NULL');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='order_record' AND column_name='unit_price'), 'SELECT 1', 'ALTER TABLE order_record ADD COLUMN unit_price DECIMAL(10,2) NULL');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='order_record' AND column_name='ship_origin'), 'SELECT 1', 'ALTER TABLE order_record ADD COLUMN ship_origin VARCHAR(80) NULL');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='advertisement' AND index_name='idx_product_owner'), 'SELECT 1', 'ALTER TABLE advertisement ADD INDEX idx_product_owner (merchant_id,ad_id)');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='advertisement' AND index_name='idx_product_review'), 'SELECT 1', 'ALTER TABLE advertisement ADD INDEX idx_product_review (review_status,is_deleted,ad_id)');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='order_record' AND index_name='idx_order_merchant'), 'SELECT 1', 'ALTER TABLE order_record ADD INDEX idx_order_merchant (merchant_id,status,order_id)');
PREPARE merchant_stmt FROM @ddl;
EXECUTE merchant_stmt;
DEALLOCATE PREPARE merchant_stmt;

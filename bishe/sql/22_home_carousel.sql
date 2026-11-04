-- 官方广告是内置内容，仅保存是否参加轮播，不提供删除功能。
CREATE TABLE IF NOT EXISTS home_carousel_config (
    id TINYINT PRIMARY KEY,
    official_enabled TINYINT(1) NOT NULL DEFAULT 1,
    interval_seconds INT NOT NULL DEFAULT 5,
    revision INT NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO home_carousel_config(id) VALUES(1);
-- 旧的已通过广告继续展示；新广告需由管理员单独加入轮播。
SET @new_carousel_column = NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='merchant_campaign' AND column_name='carousel_enabled');
SET @ddl = IF(@new_carousel_column, 'ALTER TABLE merchant_campaign ADD COLUMN carousel_enabled TINYINT(1) NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE carousel_stmt FROM @ddl;
EXECUTE carousel_stmt;
DEALLOCATE PREPARE carousel_stmt;
UPDATE merchant_campaign SET carousel_enabled=1 WHERE @new_carousel_column AND review_status='APPROVED';

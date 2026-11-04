-- 商家头像、合作方封禁，以及买家/商家各自删除已完成订单记录。

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='merchant' AND column_name='avatar'), 'SELECT 1', 'ALTER TABLE merchant ADD COLUMN avatar VARCHAR(255) NOT NULL DEFAULT ''''');
PREPARE commerce_stmt FROM @ddl;
EXECUTE commerce_stmt;
DEALLOCATE PREPARE commerce_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='merchant' AND column_name='is_banned'), 'SELECT 1', 'ALTER TABLE merchant ADD COLUMN is_banned TINYINT NOT NULL DEFAULT 0');
PREPARE commerce_stmt FROM @ddl;
EXECUTE commerce_stmt;
DEALLOCATE PREPARE commerce_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='merchant' AND column_name='ban_reason'), 'SELECT 1', 'ALTER TABLE merchant ADD COLUMN ban_reason VARCHAR(500) NOT NULL DEFAULT ''''');
PREPARE commerce_stmt FROM @ddl;
EXECUTE commerce_stmt;
DEALLOCATE PREPARE commerce_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='merchant' AND column_name='banned_at'), 'SELECT 1', 'ALTER TABLE merchant ADD COLUMN banned_at DATETIME NULL');
PREPARE commerce_stmt FROM @ddl;
EXECUTE commerce_stmt;
DEALLOCATE PREPARE commerce_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='order_record' AND column_name='buyer_deleted'), 'SELECT 1', 'ALTER TABLE order_record ADD COLUMN buyer_deleted TINYINT NOT NULL DEFAULT 0');
PREPARE commerce_stmt FROM @ddl;
EXECUTE commerce_stmt;
DEALLOCATE PREPARE commerce_stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='order_record' AND column_name='merchant_deleted'), 'SELECT 1', 'ALTER TABLE order_record ADD COLUMN merchant_deleted TINYINT NOT NULL DEFAULT 0');
PREPARE commerce_stmt FROM @ddl;
EXECUTE commerce_stmt;
DEALLOCATE PREPARE commerce_stmt;

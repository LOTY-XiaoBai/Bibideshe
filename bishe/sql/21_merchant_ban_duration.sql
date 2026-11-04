-- 限时/永久封禁；既有封禁按永久处理，不删除订单。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='merchant' AND column_name='ban_until'), 'SELECT 1', 'ALTER TABLE merchant ADD COLUMN ban_until DATETIME NULL');
PREPARE merchant_ban_stmt FROM @ddl;
EXECUTE merchant_ban_stmt;
DEALLOCATE PREPARE merchant_ban_stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='merchant' AND column_name='ban_duration'), 'SELECT 1', 'ALTER TABLE merchant ADD COLUMN ban_duration VARCHAR(16) NOT NULL DEFAULT ''PERMANENT''');
PREPARE merchant_ban_stmt FROM @ddl;
EXECUTE merchant_ban_stmt;
DEALLOCATE PREPARE merchant_ban_stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='merchant' AND index_name='idx_merchant_ban_expiry'), 'SELECT 1', 'ALTER TABLE merchant ADD INDEX idx_merchant_ban_expiry (is_banned,ban_until)');
PREPARE merchant_ban_stmt FROM @ddl;
EXECUTE merchant_ban_stmt;
DEALLOCATE PREPARE merchant_ban_stmt;
-- 升级前已经封禁的商家，也统一撤回商品及广告。
UPDATE advertisement p JOIN merchant m ON m.merchant_id=p.merchant_id SET p.is_deleted=1,p.review_status='WITHDRAWN',p.review_reason='商家已封禁，商品已下架',p.revision=p.revision+1 WHERE m.is_banned=1 AND (p.is_deleted=0 OR p.review_status<>'WITHDRAWN');
UPDATE merchant_campaign c JOIN merchant m ON m.merchant_id=c.merchant_id SET c.review_status='WITHDRAWN',c.review_reason='商家已封禁，广告已撤销',c.revision=c.revision+1 WHERE m.is_banned=1 AND c.review_status<>'WITHDRAWN';

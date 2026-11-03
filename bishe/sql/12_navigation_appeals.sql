-- 社区分页、申诉、未读游标与警告衰减；仅结构，可重复执行。
SET NAMES utf8mb4;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='community_user' AND column_name='warning_decay_at'),'SELECT 1','ALTER TABLE community_user ADD COLUMN warning_decay_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''警告每24小时减1的结算锚点''');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='community_report' AND column_name='muted_until'),'SELECT 1','ALTER TABLE community_report ADD COLUMN muted_until DATETIME NULL COMMENT ''本次举报处置的禁言截止时间''');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
CREATE TABLE IF NOT EXISTS community_appeal (
 id BIGINT NOT NULL AUTO_INCREMENT,
 report_id BIGINT NOT NULL,
 owner VARCHAR(90) NOT NULL,
 content TEXT NOT NULL COMMENT '100至3000字申诉说明',
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 feedback VARCHAR(500) NOT NULL DEFAULT '',
 reviewer VARCHAR(90) NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_at DATETIME NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_appeal(report_id), KEY idx_appeal_status(status,id), KEY idx_appeal_owner(owner,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报处置申诉记录';
CREATE TABLE IF NOT EXISTS chat_read_cursor (
 owner VARCHAR(90) NOT NULL,
 kind VARCHAR(10) NOT NULL,
 target_id BIGINT NOT NULL,
 last_message_id BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(owner,kind,target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会话已读消息游标';

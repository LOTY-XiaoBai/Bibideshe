-- 允许对管理员直接隐藏/删除的帖子提交申诉，不依赖举报记录。
ALTER TABLE community_appeal MODIFY COLUMN report_id BIGINT NULL;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='community_appeal' AND column_name='operation_id'),'SELECT 1','ALTER TABLE community_appeal ADD COLUMN operation_id BIGINT NULL, ADD UNIQUE KEY uk_appeal_operation(operation_id)');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

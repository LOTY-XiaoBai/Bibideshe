SET NAMES utf8mb4;
-- 使用当前数据库，不切换到固定库名。
CREATE TABLE IF NOT EXISTS admin_operation_log (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作记录ID',
 operator VARCHAR(90) NOT NULL COMMENT '操作人：ADMIN:管理员账号或SYSTEM',
 action VARCHAR(40) NOT NULL COMMENT '操作名称：封禁、禁言、下架、删帖等',
 target_type VARCHAR(30) NOT NULL COMMENT '对象类型：POST、REPLY、USER、WORD、CHAT、GROUP、REPORT',
 target_id VARCHAR(90) NOT NULL COMMENT '操作对象ID',
 target_owner VARCHAR(90) NOT NULL DEFAULT '' COMMENT '被操作用户，角色:账号',
 post_id BIGINT NULL COMMENT '关联帖子ID',
 post_title VARCHAR(120) NOT NULL DEFAULT '' COMMENT '操作时的帖子标题快照',
 details VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '操作说明与对象内容摘要',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
 PRIMARY KEY(id), KEY idx_operation_time(created_at), KEY idx_operation_post(post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作留痕记录表';
CREATE TABLE IF NOT EXISTS app_schema_migration (
 version VARCHAR(80) NOT NULL COMMENT '已执行的数据迁移标识',
 applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '迁移完成时间',
 PRIMARY KEY(version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目增量数据迁移记录表';
SET @friend_time_missing=(SELECT COUNT(*)=0 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='friend' AND COLUMN_NAME='created_at');
SET @ddl=IF(@friend_time_missing,'ALTER TABLE friend ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''好友建立时间，历史记录以已有申请时间回填''','SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
START TRANSACTION;
UPDATE friend f SET created_at=COALESCE((SELECT MAX(r.apply_time) FROM friend_request r WHERE r.status=1 AND ((r.requester_id=f.friend_a_id AND r.receiver_id=f.friend_b_id) OR (r.requester_id=f.friend_b_id AND r.receiver_id=f.friend_a_id))),f.created_at)
WHERE NOT EXISTS(SELECT 1 FROM app_schema_migration WHERE version='11_community_management');
INSERT IGNORE INTO special_word(word,enabled,created_by)
SELECT word,1,'migration' FROM community_word WHERE NOT EXISTS(SELECT 1 FROM app_schema_migration WHERE version='11_community_management');
INSERT IGNORE INTO app_schema_migration(version) VALUES('11_community_management');
COMMIT;
ALTER TABLE special_word COMMENT='社区与聊天共用特殊词表';
ALTER TABLE community_word COMMENT='旧社区专用词库（已合并到special_word，停用）';

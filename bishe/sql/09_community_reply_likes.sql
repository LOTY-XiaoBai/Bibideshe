-- 帖子样式升级配套增量，可重复执行；先执行07迁移。
SET NAMES utf8mb4;
USE bishe;
CREATE TABLE IF NOT EXISTS community_reply_like (
 reply_id BIGINT NOT NULL COMMENT '被点赞的回复ID，对应community_reply.id',
 owner VARCHAR(90) NOT NULL COMMENT '点赞用户唯一标识，格式为角色:账号',
 PRIMARY KEY(reply_id,owner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区回复点赞记录表';
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='community_post' AND column_name='share_count'),
 'SELECT ''share_count already exists''',
 'ALTER TABLE community_post ADD COLUMN share_count INT NOT NULL DEFAULT 0 COMMENT ''成功转发到私聊或群聊的次数''');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

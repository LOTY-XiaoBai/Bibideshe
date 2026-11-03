SET NAMES utf8mb4;
-- 使用当前数据库，不切换到固定库名。
-- 帖子关注入口复用已有 friend_request / friend，不再新建独立关注表。
CREATE TABLE IF NOT EXISTS community_block (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '屏蔽记录ID',
 owner VARCHAR(90) NOT NULL COMMENT '屏蔽操作人，格式为角色:账号',
 kind VARCHAR(12) NOT NULL COMMENT '屏蔽类型：POST单帖、AUTHOR作者所有帖子',
 target VARCHAR(90) NOT NULL COMMENT '单帖为帖子ID字符串，作者为角色:账号',
 label VARCHAR(160) NOT NULL COMMENT '屏蔽时的帖子标题或作者名称',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '屏蔽时间',
 PRIMARY KEY(id), UNIQUE KEY uk_block(owner,kind,target)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区个人屏蔽记录表';
CREATE TABLE IF NOT EXISTS community_report (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '举报记录ID',
 reporter VARCHAR(90) NOT NULL COMMENT '举报人，格式为角色:账号',
 post_id BIGINT NOT NULL COMMENT '被举报帖子ID',
 post_owner VARCHAR(90) NOT NULL COMMENT '被举报作者，格式为角色:账号',
 post_title VARCHAR(120) NOT NULL COMMENT '举报时的帖子标题快照',
 content_snapshot MEDIUMTEXT NOT NULL COMMENT '举报时的正文文字快照',
 reason VARCHAR(40) NOT NULL COMMENT '举报原因分类',
 details VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '举报补充说明',
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING待处理、RESOLVED已处置、DISMISSED不成立',
 decision VARCHAR(16) NULL COMMENT '处理结果：hide下架、delete删帖、dismiss驳回',
 feedback VARCHAR(500) NOT NULL DEFAULT '' COMMENT '管理员处理说明',
 reviewer VARCHAR(90) NULL COMMENT '处理管理员，格式为角色:账号',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报提交时间',
 reviewed_at DATETIME NULL COMMENT '举报处理时间',
 PRIMARY KEY(id), UNIQUE KEY uk_report(reporter,post_id), KEY idx_report_status(status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子举报与管理员处理记录表';

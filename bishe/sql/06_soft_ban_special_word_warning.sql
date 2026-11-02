-- ========================================================
-- 06_soft_ban_special_word_warning.sql
-- 描述: 师生账号软删除/封禁、特殊词监视、用户警告
-- 用法: 在 Navicat / MySQL 客户端手动执行，勿在应用启动时自动执行
-- ========================================================

SET NAMES utf8mb4;

-- ---------- 1. 学生/教师软删除与封禁 ----------
ALTER TABLE `student`
  ADD COLUMN `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0正常, 1已注销/删除' AFTER `comprehensive_score`,
  ADD COLUMN `is_banned` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '封禁: 0正常, 1已封禁' AFTER `is_deleted`,
  ADD COLUMN `ban_reason` VARCHAR(500) DEFAULT NULL COMMENT '封禁理由' AFTER `is_banned`,
  ADD COLUMN `banned_by` VARCHAR(20) DEFAULT NULL COMMENT '执行封禁的管理员ID' AFTER `ban_reason`,
  ADD COLUMN `banned_at` DATETIME DEFAULT NULL COMMENT '封禁时间' AFTER `banned_by`;

ALTER TABLE `teacher`
  ADD COLUMN `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0正常, 1已注销/删除' AFTER `course_count`,
  ADD COLUMN `is_banned` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '封禁: 0正常, 1已封禁' AFTER `is_deleted`,
  ADD COLUMN `ban_reason` VARCHAR(500) DEFAULT NULL COMMENT '封禁理由' AFTER `is_banned`,
  ADD COLUMN `banned_by` VARCHAR(20) DEFAULT NULL COMMENT '执行封禁的管理员ID' AFTER `ban_reason`,
  ADD COLUMN `banned_at` DATETIME DEFAULT NULL COMMENT '封禁时间' AFTER `banned_by`;

-- ---------- 2. 特殊词表 ----------
CREATE TABLE IF NOT EXISTS `special_word` (
  `word_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '特殊词ID',
  `word` VARCHAR(100) NOT NULL COMMENT '特殊词内容',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用: 1启用, 0停用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` VARCHAR(20) DEFAULT NULL COMMENT '创建人(管理员ID)',
  PRIMARY KEY (`word_id`),
  UNIQUE KEY `uk_special_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天特殊词监视表';

-- ---------- 3. 特殊词命中/违规处理记录 ----------
CREATE TABLE IF NOT EXISTS `chat_violation` (
  `violation_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '违规记录ID',
  `message_id` BIGINT NOT NULL COMMENT '关联聊天消息ID',
  `word_id` BIGINT DEFAULT NULL COMMENT '命中的特殊词ID',
  `matched_word` VARCHAR(100) NOT NULL COMMENT '命中词内容快照',
  `sender_id` BIGINT NOT NULL COMMENT '发送人ID',
  `receiver_id` BIGINT DEFAULT 0 COMMENT '私聊接收人ID, 群聊为0',
  `is_private` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否私聊: 1是, 0群聊',
  `group_id` BIGINT DEFAULT 0 COMMENT '群号, 私聊为0',
  `content_snapshot` TEXT COMMENT '消息内容快照',
  `hit_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '命中时间',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0待处理, 1已忽视, 2已警告, 3已封禁',
  `handled_by` VARCHAR(20) DEFAULT NULL COMMENT '处理管理员ID',
  `handled_at` DATETIME DEFAULT NULL COMMENT '处理时间',
  `handle_note` VARCHAR(500) DEFAULT NULL COMMENT '处理备注',
  PRIMARY KEY (`violation_id`),
  KEY `idx_violation_status` (`status`),
  KEY `idx_violation_message` (`message_id`),
  KEY `idx_violation_hit_time` (`hit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='特殊词命中与处理记录';

-- ---------- 4. 用户警告表 ----------
CREATE TABLE IF NOT EXISTS `user_warning` (
  `warning_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '警告ID',
  `user_id` BIGINT NOT NULL COMMENT '被警告用户ID(学生/教师)',
  `content` VARCHAR(1000) NOT NULL COMMENT '警告内容',
  `reason` VARCHAR(500) DEFAULT NULL COMMENT '触发原因',
  `related_message_id` BIGINT DEFAULT NULL COMMENT '关联聊天消息ID',
  `related_violation_id` BIGINT DEFAULT NULL COMMENT '关联违规记录ID',
  `created_by` VARCHAR(20) DEFAULT NULL COMMENT '发出警告的管理员ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读弹窗: 0未读, 1已读',
  `is_dismissed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否不再显示(私聊保留区): 0显示, 1隐藏',
  `chat_message_id` BIGINT DEFAULT NULL COMMENT '写入私聊的系统消息ID',
  PRIMARY KEY (`warning_id`),
  KEY `idx_warning_user` (`user_id`, `is_read`, `is_dismissed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户警告记录表';

-- ---------- 5. 预置特殊词(可按需增删) ----------
INSERT INTO `special_word` (`word`, `enabled`, `created_by`) VALUES
('傻逼', 1, 'system'),
('去死', 1, 'system'),
('废物', 1, 'system'),
('白痴', 1, 'system'),
('混蛋', 1, 'system'),
('滚蛋', 1, 'system'),
('操', 1, 'system'),
('妈的', 1, 'system')
ON DUPLICATE KEY UPDATE `enabled` = VALUES(`enabled`);

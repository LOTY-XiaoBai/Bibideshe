-- ========================================================
-- V1__init_schema_part1.sql
-- 描述: 校园教学社交一体化平台 - 基础结构初始化 (全量 25 表)
-- 包含: 用户体系、社交体系、广告商城体系、题库与考试作业体系、签到模块等
-- 注意: 已将所有历史 ALTER TABLE 补丁合并至对应建表语句中
-- ========================================================

-- 设置字符集，生产环境推荐强制 utf8mb4 以支持 Emoji
SET NAMES utf8mb4;

-- 1. 学生账号表
CREATE TABLE `student` (
  `student_id` BIGINT NOT NULL COMMENT '学生ID(1开头)',
  `nickname` VARCHAR(50) NOT NULL COMMENT '学生昵称',
  `password` VARCHAR(255) NOT NULL COMMENT '学生密码',
  `signature` VARCHAR(255) COMMENT '学生个性签名',
  `avatar` VARCHAR(255) COMMENT '学生头像(URL)',
  `comprehensive_score` DECIMAL(5,2) DEFAULT 0.00 COMMENT '综合评分',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0正常, 1已注销/删除',
  `is_banned` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '封禁: 0正常, 1已封禁',
  `ban_reason` VARCHAR(500) DEFAULT NULL COMMENT '封禁理由',
  `banned_by` VARCHAR(20) DEFAULT NULL COMMENT '执行封禁的管理员ID',
  `banned_at` DATETIME DEFAULT NULL COMMENT '封禁时间',
  PRIMARY KEY (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生账号表';

-- 2. 教师账号表
CREATE TABLE `teacher` (
  `teacher_id` BIGINT NOT NULL COMMENT '教师ID(2开头)',
  `name` VARCHAR(50) NOT NULL COMMENT '教师姓名',
  `signature` VARCHAR(255) COMMENT '教师个性签名',
  `password` VARCHAR(255) NOT NULL COMMENT '教师密码',
  `avatar` VARCHAR(255) COMMENT '教师头像(URL)',
  `comprehensive_score` DECIMAL(5,2) DEFAULT 0.00 COMMENT '综合评分',
  `course_count` INT DEFAULT 0 COMMENT '已有课程数量',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0正常, 1已注销/删除',
  `is_banned` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '封禁: 0正常, 1已封禁',
  `ban_reason` VARCHAR(500) DEFAULT NULL COMMENT '封禁理由',
  `banned_by` VARCHAR(20) DEFAULT NULL COMMENT '执行封禁的管理员ID',
  `banned_at` DATETIME DEFAULT NULL COMMENT '封禁时间',
  PRIMARY KEY (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师账号表';

-- 3. 管理员账号表 (已将 admin_id 类型合并为 VARCHAR)
CREATE TABLE `admin` (
  `admin_id` VARCHAR(20) NOT NULL COMMENT '管理员ID',
  `password` VARCHAR(255) NOT NULL COMMENT '管理员密码',
  PRIMARY KEY (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员账号表';

-- 4. 广告表 (已合并 title, price, create_time, is_deleted, publisher_id 类型变更)
CREATE TABLE `advertisement` (
  `ad_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '广告ID',
  `title` VARCHAR(100) COMMENT '商品/广告标题',
  `content` TEXT COMMENT '广告内容',
  `image_url` VARCHAR(255) COMMENT '广告图片URL',
  `price` DECIMAL(10,2) DEFAULT 0.00 COMMENT '商品价格',
  `publisher_id` VARCHAR(20) NOT NULL COMMENT '广告发布者ID(联动管理员ID)',
  `score` DECIMAL(3,1) DEFAULT 0.0 COMMENT '广告打分',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `is_deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除: 0正常, 1已下架',
  PRIMARY KEY (`ad_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告表';

-- 5. 好友表
CREATE TABLE `friend` (
  `friend_a_id` BIGINT NOT NULL COMMENT '好友AID(学生/教师ID)',
  `friend_b_id` BIGINT NOT NULL COMMENT '好友BID(学生/教师ID)',
  PRIMARY KEY (`friend_a_id`, `friend_b_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友表';

-- 5.1 好友申请表
CREATE TABLE `friend_request` (
  `request_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `requester_id` BIGINT NOT NULL COMMENT '申请人ID(学生/教师ID)',
  `receiver_id` BIGINT NOT NULL COMMENT '被申请人ID(学生/教师ID)',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0待处理, 1已同意, 2已拒绝',
  `apply_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  PRIMARY KEY (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友申请表';

-- 6. 聊天记录表 (已合并 receiver_id 私聊接收人与 is_deleted 软删控制)
CREATE TABLE `chat_record` (
  `message_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息记录ID',
  `send_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `sender_id` BIGINT NOT NULL COMMENT '发送人ID',
  `receiver_id` BIGINT DEFAULT 0 COMMENT '私聊接收人ID, 群聊为0',
  `is_private` TINYINT(1) DEFAULT 0 COMMENT '是否私聊: 1是, 0群聊',
  `group_id` BIGINT DEFAULT 0 COMMENT '群聊群号(没有就是0)',
  `content` TEXT COMMENT '消息内容',
  `message_type` INT COMMENT '消息类型: 0文本, 1图片等',
  `reference_id` BIGINT DEFAULT 0 COMMENT '被引用ID(没有就是0)',
  `is_deleted` TINYINT(1) DEFAULT 0 COMMENT '用户撤回=软删，管理员仍可查全部',
  PRIMARY KEY (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天记录表';

-- 7. 群聊与用户表
CREATE TABLE `group_user` (
  `group_id` BIGINT NOT NULL COMMENT '加入群聊ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID(教师/学生ID)',
  PRIMARY KEY (`group_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊与用户关联表';

-- 8. 群聊表 (已合并群名、头像、简介、解散及管理员封禁状态字段)
CREATE TABLE `group_chat` (
  `group_id` BIGINT NOT NULL COMMENT '群号ID',
  `group_name` VARCHAR(100) COMMENT '群聊名称',
  `group_avatar` VARCHAR(255) COMMENT '群头像(URL)',
  `group_description` VARCHAR(500) COMMENT '群简介',
  `total_people` INT DEFAULT 1 COMMENT '总人数',
  `is_dissolved` TINYINT(1) DEFAULT 0 COMMENT '解散=标记，聊天记录仍可查',
  `is_deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除: 0正常, 1系统管理员软删除',
  `is_banned` TINYINT(1) DEFAULT 0 COMMENT '封禁: 0正常, 1系统管理员封禁(非解散)',
  `ban_reason` VARCHAR(500) COMMENT '封禁理由',
  `banned_by` VARCHAR(20) COMMENT '执行封禁的系统管理员ID',
  `banned_at` DATETIME COMMENT '封禁时间',
  PRIMARY KEY (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊基本信息表';

-- 9. 群聊与管理ID表 (已合并管理员权限等级字段)
CREATE TABLE `group_admin` (
  `group_id` BIGINT NOT NULL COMMENT '群号ID',
  `admin_id` BIGINT NOT NULL COMMENT '管理员ID(学生/教师ID)',
  `admin_level` TINYINT DEFAULT 1 COMMENT '管理员等级: 1=群主, 2=普通管理员',
  PRIMARY KEY (`group_id`, `admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊管理员表';

-- 10. 商品/订单表 (已合并多身份买家、时间、状态流转和售后处理体系)
CREATE TABLE `order_record` (
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `ad_id` BIGINT NOT NULL COMMENT '广告ID',
  `buyer_id` BIGINT NOT NULL COMMENT '买家ID(学生/教师ID)',
  `is_received` TINYINT(1) DEFAULT 0 COMMENT '是否收货: 0否, 1是',
  `logistics_info` VARCHAR(255) COMMENT '物流信息',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `status` TINYINT DEFAULT 0 COMMENT '订单状态: 0待发货, 1已发货, 2已收货',
  `after_sale_content` TEXT COMMENT '售后申请内容',
  `after_sale_status` TINYINT DEFAULT 0 COMMENT '售后状态: 0无, 1申请中, 2已处理',
  `after_sale_time` DATETIME COMMENT '售后申请时间',
  `after_sale_reply` VARCHAR(500) COMMENT '管理员售后回复',
  PRIMARY KEY (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品订单表';

-- 10.1 订单评价表 (买家关联字段重命名适配)
CREATE TABLE `order_evaluation` (
  `evaluation_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `buyer_id` BIGINT NOT NULL COMMENT '买家ID(学生/教师ID)',
  `rating` INT COMMENT '评价星级',
  `feedback_content` TEXT COMMENT '文字反馈内容',
  `publish_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`evaluation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单评价表';

-- 11. 选择题表
CREATE TABLE `question_choice` (
  `question_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  `content` TEXT NOT NULL COMMENT '题目内容',
  `answer` VARCHAR(255) NOT NULL COMMENT '答案',
  `publisher_id` BIGINT NOT NULL COMMENT '发布者ID(教师ID)',
  `is_public` TINYINT(1) DEFAULT 0 COMMENT '是否公开',
  `audit_status` TINYINT(1) DEFAULT 0 COMMENT '审核状态: 默认为0没公开',
  PRIMARY KEY (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选择题表';

-- 11.1 选择题选项表 (已补充选项内容字段)
CREATE TABLE `question_choice_option` (
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `option_code` VARCHAR(10) NOT NULL COMMENT '选项编号(如A,B,C,D)',
  `option_content` VARCHAR(500) COMMENT '选项内容',
  PRIMARY KEY (`question_id`, `option_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选择题选项表';

-- 12. 判断题表
CREATE TABLE `question_judge` (
  `question_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  `content` TEXT NOT NULL COMMENT '题目内容',
  `answer` VARCHAR(255) NOT NULL COMMENT '答案',
  `publisher_id` BIGINT NOT NULL COMMENT '发布者ID(教师ID)',
  `is_public` TINYINT(1) DEFAULT 0 COMMENT '是否公开',
  `audit_status` TINYINT(1) DEFAULT 0 COMMENT '审核状态(默认为0没公开)',
  PRIMARY KEY (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='判断题表';

-- 13. 解答题表
CREATE TABLE `question_answer` (
  `question_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  `content` TEXT NOT NULL COMMENT '题目内容',
  `answer` TEXT NOT NULL COMMENT '答案',
  `publisher_id` BIGINT NOT NULL COMMENT '发布者ID(教师ID)',
  `is_public` TINYINT(1) DEFAULT 0 COMMENT '是否公开',
  `audit_status` TINYINT(1) DEFAULT 0 COMMENT '审核状态(默认为0没公开)',
  PRIMARY KEY (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='解答题表';

-- 14. 考试表 (已合并成绩公布控制配置)
CREATE TABLE `exam` (
  `exam_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '考试ID',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `publisher_id` BIGINT NOT NULL COMMENT '发布者ID(教师ID)',
  `time_limit` INT NOT NULL COMMENT '考试限制时间(分钟)',
  `hide_result_until_publish` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=需教师公布后才可查看成绩/答案',
  `is_result_published` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=教师已公布成绩/答案',
  PRIMARY KEY (`exam_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试表';

-- 15. 考试题目表 (联合主键已补全 question_type)
CREATE TABLE `exam_question` (
  `exam_id` BIGINT NOT NULL COMMENT '考试ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `question_type` TINYINT NOT NULL COMMENT '题目类型: 1选择题, 2判断题, 3解答题',
  `score` DECIMAL(5,1) NOT NULL COMMENT '题目分值',
  PRIMARY KEY (`exam_id`, `question_id`, `question_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试题目关联表';

-- 15.1 考试题目答题情况 (联合主键已补全 question_type)
CREATE TABLE `exam_question_answer` (
  `exam_id` BIGINT NOT NULL COMMENT '考试ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `question_type` TINYINT NOT NULL COMMENT '题目类型: 1选择题, 2判断题, 3解答题',
  `student_id` BIGINT NOT NULL COMMENT '学生ID',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `score` DECIMAL(5,1) DEFAULT 0.0 COMMENT '分数',
  `annotation` TEXT COMMENT '批注(可有可无)',
  `student_answer` TEXT COMMENT '学生答案',
  PRIMARY KEY (`exam_id`, `question_id`, `question_type`, `student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试题目答题情况表';

-- 16. 用户考试记录表 (已合并开始时间字段，用于限时校验)
CREATE TABLE `user_exam_record` (
  `student_id` BIGINT NOT NULL COMMENT '学生ID',
  `exam_id` BIGINT NOT NULL COMMENT '考试ID',
  `exam_score` DECIMAL(5,1) DEFAULT 0.0 COMMENT '考试得分',
  `teacher_score` DECIMAL(5,1) DEFAULT 0.0 COMMENT '教师评分',
  `start_time` DATETIME DEFAULT NULL COMMENT '开始考试时间(用于限时校验)',
  PRIMARY KEY (`student_id`, `exam_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户考试总记录表';

-- 17. 作业表 (已合并成绩公布控制配置)
CREATE TABLE `homework` (
  `homework_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '作业ID',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `publisher_id` BIGINT NOT NULL COMMENT '发布者ID(教师ID)',
  `deadline` DATETIME NOT NULL COMMENT '截至时间',
  `hide_result_until_publish` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=需教师公布后才可查看成绩/答案',
  `is_result_published` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=教师已公布成绩/答案',
  PRIMARY KEY (`homework_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业主表';

-- 18. 作业题目表 (已补全 question_type 联合主键)
CREATE TABLE `homework_question` (
  `homework_id` BIGINT NOT NULL COMMENT '作业ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `question_type` TINYINT NOT NULL COMMENT '题目类型: 1选择题, 2判断题, 3解答题',
  `score` DECIMAL(5,1) NOT NULL COMMENT '题目分值',
  PRIMARY KEY (`homework_id`, `question_id`, `question_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业题目关联表';

-- 18.1 作业题目答题情况 (已补全 question_type 联合主键)
CREATE TABLE `homework_question_answer` (
  `homework_id` BIGINT NOT NULL COMMENT '作业ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `question_type` TINYINT NOT NULL COMMENT '题目类型: 1选择题, 2判断题, 3解答题',
  `student_id` BIGINT NOT NULL COMMENT '学生ID',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `score` DECIMAL(5,1) DEFAULT 0.0 COMMENT '分数',
  `annotation` TEXT COMMENT '批注(可有可无)',
  `student_answer` TEXT COMMENT '学生答案',
  PRIMARY KEY (`homework_id`, `question_id`, `question_type`, `student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业题目答题情况表';

-- 19. 用户作业记录
CREATE TABLE `user_homework_record` (
  `student_id` BIGINT NOT NULL COMMENT '学生ID',
  `homework_id` BIGINT NOT NULL COMMENT '作业ID',
  `homework_score` DECIMAL(5,1) DEFAULT 0.0 COMMENT '作业得分',
  `teacher_score` DECIMAL(5,1) DEFAULT 0.0 COMMENT '教师评分',
  PRIMARY KEY (`student_id`, `homework_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户作业总记录表';

-- 20. 教师评分记录 (已合并关联学生、课程、时间及唯一约束)
CREATE TABLE `teacher_evaluation` (
  `evaluation_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '打分ID(主键)',
  `teacher_id` BIGINT NOT NULL COMMENT '教师ID',
  `student_id` BIGINT NOT NULL COMMENT '打分学生ID',
  `course_id` BIGINT NOT NULL COMMENT '关联课程ID',
  `score` DECIMAL(3,1) NOT NULL COMMENT '评分星级/分数',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '打分时间',
  PRIMARY KEY (`evaluation_id`),
  UNIQUE KEY `uk_teacher_student` (`teacher_id`, `student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师评分记录表';

-- 21. 课程表
CREATE TABLE `course` (
  `course_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  `teacher_id` BIGINT NOT NULL COMMENT '主讲人ID(教师ID)',
  `course_name` VARCHAR(100) NOT NULL COMMENT '课程名字',
  `is_finished` TINYINT(1) DEFAULT 0 COMMENT '是否结束: 0否, 1是',
  PRIMARY KEY (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程主表';

-- 22. 课程人员表
CREATE TABLE `course_personnel` (
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `student_id` BIGINT NOT NULL COMMENT '学生ID',
  `student_score` DECIMAL(5,1) DEFAULT 0.0 COMMENT '学生分数',
  PRIMARY KEY (`course_id`, `student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课与课程成绩表';

-- 23. 签到记录表 (已合并发布者、状态、分值及结束时间字段)
CREATE TABLE `check_in_record` (
  `check_in_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '签到ID',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `publisher_id` BIGINT NOT NULL COMMENT '发布者ID(教师ID)',
  `publish_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '签到时间(发布时间)',
  `deadline` DATETIME NOT NULL COMMENT '截止时间',
  `end_time` DATETIME COMMENT '实际结束时间',
  `attendee_count` INT DEFAULT 0 COMMENT '签到人数',
  `status` TINYINT DEFAULT 0 COMMENT '签到状态: 0进行中, 1已结束',
  `score_value` DECIMAL(5,1) NOT NULL DEFAULT 1.0 COMMENT '本次签到分值(计入课程签到得分)',
  PRIMARY KEY (`check_in_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到发布记录表';

-- 24. 签到与用户表
CREATE TABLE `check_in_user` (
  `check_in_id` BIGINT NOT NULL COMMENT '签到ID',
  `student_id` BIGINT NOT NULL COMMENT '学生ID',
  `check_in_time` DATETIME COMMENT '实际签到时间',
  `status` TINYINT COMMENT '签到状态(例如: 0缺勤, 1已签到, 2迟到)',
  PRIMARY KEY (`check_in_id`, `student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生签到明细表';

-- 25. 错题本表 (已补全 question_type 联合主键)
CREATE TABLE `error_book` (
  `student_id` BIGINT NOT NULL COMMENT '学生ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `question_type` TINYINT NOT NULL COMMENT '题目类型: 1选择题, 2判断题, 3解答题',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (`student_id`, `question_id`, `question_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生错题本';

-- ========================================================
-- 新增: 师生账号软删除/封禁、特殊词监视、用户警告体系 (表 26-28)
-- ========================================================

-- 26. 特殊词表
CREATE TABLE `special_word` (
  `word_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '特殊词ID',
  `word` VARCHAR(100) NOT NULL COMMENT '特殊词内容',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用: 1启用, 0停用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` VARCHAR(20) DEFAULT NULL COMMENT '创建人(管理员ID)',
  PRIMARY KEY (`word_id`),
  UNIQUE KEY `uk_special_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天特殊词监视表';

-- 27. 特殊词命中/违规处理记录
CREATE TABLE `chat_violation` (
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

-- 28. 用户警告表
CREATE TABLE `user_warning` (
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

-- 写入预置特殊词数据
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
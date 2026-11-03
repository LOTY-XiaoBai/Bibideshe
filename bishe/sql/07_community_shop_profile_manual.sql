-- 程序自动执行的07版本结构迁移，无需在Navicat中手工导入。
-- manual为历史文件名，为兼容已执行的迁移标识而保留。
-- 仅补齐结构和注释，不删除业务数据；支持重复执行。
SET NAMES utf8mb4;
-- 使用当前数据库，不切换到固定库名。
SELECT @@hostname AS server_host, @@port AS server_port,
       @@server_uuid AS server_uuid, DATABASE() AS database_name;

CREATE TABLE IF NOT EXISTS `user_extra` (
  `owner` VARCHAR(90) NOT NULL COMMENT '用户唯一标识，格式为角色:账号，如 STUDENT:10001',
  `gender` VARCHAR(12) NOT NULL DEFAULT '保密' COMMENT '性别：保密、男、女',
  `birth_date` DATE NULL DEFAULT NULL COMMENT '出生日期',
  `school` VARCHAR(120) NOT NULL DEFAULT '' COMMENT '学校名称',
  `education` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '学历',
  `recipient` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '收货人姓名',
  `phone` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '收货联系电话',
  `address` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '完整收货地址，含省市区、街道和门牌号',
  PRIMARY KEY (owner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户扩展资料与收货地址表';
CREATE TABLE IF NOT EXISTS `product_detail` (
  `ad_id` BIGINT NOT NULL COMMENT '商品ID，对应 advertisement.ad_id',
  `origin` VARCHAR(120) NOT NULL DEFAULT '' COMMENT '商品发货地',
  `blocks` MEDIUMTEXT NOT NULL COMMENT '图文段落JSON数组，包含文字段落和站内图片地址',
  PRIMARY KEY (ad_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图文详情与发货地表';
-- 单独检测两个字段，兼容已执行、未执行或部分执行的数据库。
SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='order_record' AND column_name='received_time'),
  'SELECT ''received_time already exists'' AS migration_note',
  'ALTER TABLE order_record ADD COLUMN received_time DATETIME NULL COMMENT ''收货确认时间，满7天且未评价时自动生成默认好评'''
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='order_record' AND column_name='shipping_snapshot'),
  'SELECT ''shipping_snapshot already exists'' AS migration_note',
  'ALTER TABLE order_record ADD COLUMN shipping_snapshot VARCHAR(700) NULL COMMENT ''下单时的收货人、电话及地址快照'''
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
CREATE TABLE IF NOT EXISTS `community_user` (
  `owner` VARCHAR(90) NOT NULL COMMENT '用户唯一标识，格式为角色:账号',
  `warnings` INT NOT NULL DEFAULT 0 COMMENT '累计违规回复警告次数，超过3次触发社区禁言',
  `muted_until` DATETIME NULL DEFAULT NULL COMMENT '社区禁言截止时间，NULL表示未禁言',
  PRIMARY KEY (owner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区用户警告与禁言记录表';
CREATE TABLE IF NOT EXISTS `community_post` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '帖子ID，自动生成',
  `owner` VARCHAR(90) NOT NULL COMMENT '发帖用户唯一标识，格式为角色:账号',
  `author` VARCHAR(100) NOT NULL COMMENT '发布时的作者显示名称',
  `title` VARCHAR(120) NOT NULL COMMENT '帖子标题',
  `blocks` MEDIUMTEXT NOT NULL COMMENT '帖子图文段落JSON数组，包含文字和站内图片',
  `search_text` MEDIUMTEXT NOT NULL COMMENT '从图文中提取的纯文字，供搜索和监控词检测',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT '帖子状态：PUBLIC公开、HIDDEN作者隐藏、FORCED管理员强制隐藏、DELETED已删除',
  `featured` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否精华帖：0否、1是',
  `pinned` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否置顶帖：0否、1是',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次发布时间，编辑或重新发布不刷新',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近编辑时间，由业务代码更新',
  PRIMARY KEY (id),
  INDEX idx_feed (status,pinned,created_at),
  INDEX idx_owner (owner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子表';
CREATE TABLE IF NOT EXISTS `community_reply` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '回复ID，自动生成',
  `post_id` BIGINT NOT NULL COMMENT '所属帖子ID，对应 community_post.id',
  `owner` VARCHAR(90) NOT NULL COMMENT '回复用户唯一标识，格式为角色:账号',
  `author` VARCHAR(100) NOT NULL COMMENT '回复时的作者显示名称',
  `content` TEXT NOT NULL COMMENT '回复正文，命中屏蔽词的部分以星号替换后保存',
  `parent_id` BIGINT NULL DEFAULT NULL COMMENT '被回复的回复ID，NULL表示直接回复帖子',
  `deleted` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记：0正常、1已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回复发布时间',
  PRIMARY KEY (id),
  INDEX idx_post (post_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子回复表';
CREATE TABLE IF NOT EXISTS `community_view` (
  `post_id` BIGINT NOT NULL COMMENT '被浏览的帖子ID，对应 community_post.id',
  `owner` VARCHAR(90) NOT NULL COMMENT '浏览用户唯一标识，格式为角色:账号',
  `view_day` DATE NOT NULL COMMENT '浏览日期，同一账号每天对同一帖子仅计一次浏览',
  PRIMARY KEY (post_id,owner,view_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子每日浏览去重记录表';
CREATE TABLE IF NOT EXISTS `community_like` (
  `post_id` BIGINT NOT NULL COMMENT '被点赞的帖子ID，对应 community_post.id',
  `owner` VARCHAR(90) NOT NULL COMMENT '点赞用户唯一标识，同一账号对同一帖最多保留一条有效点赞',
  PRIMARY KEY (post_id,owner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子点赞记录表';
CREATE TABLE IF NOT EXISTS `community_notice` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID，自动生成',
  `owner` VARCHAR(90) NOT NULL COMMENT '接收通知的用户唯一标识，格式为角色:账号',
  `post_id` BIGINT NULL DEFAULT NULL COMMENT '关联帖子ID，NULL表示无关联帖子',
  `kind` VARCHAR(30) NOT NULL COMMENT '通知类型：FEATURED精华、REPLY回复、LIKE点赞、FORCED下架、DELETED删帖、REPLY_DELETED删回复、WARNING警告',
  `message` VARCHAR(500) NOT NULL COMMENT '通知内容',
  `is_read` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已读：0未读、1已读',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '通知创建时间',
  PRIMARY KEY (id),
  INDEX idx_notice (owner,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区互动与管理通知表';
CREATE TABLE IF NOT EXISTS `community_word` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '监控词ID，自动生成',
  `word` VARCHAR(80) NOT NULL COMMENT '监控词原文，唯一；发帖命中则拒发，回复命中则替换并警告',
  PRIMARY KEY (id),
  UNIQUE KEY (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子与回复专用监控词表';
INSERT IGNORE INTO community_word(word) VALUES ('操你妈');

-- 验收结果应包含全部 9 张表，以及 order_record 的两个新增字段。
SELECT table_name FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name IN
('user_extra','product_detail','community_user','community_post','community_reply',
 'community_view','community_like','community_notice','community_word')
ORDER BY table_name;
SELECT column_name,column_type FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='order_record'
AND column_name IN ('received_time','shipping_snapshot');

-- 已存在的表也同步补齐中文注释。

ALTER TABLE `user_extra`
  COMMENT = '用户扩展资料与收货地址表',
  MODIFY COLUMN `owner` VARCHAR(90) NOT NULL COMMENT '用户唯一标识，格式为角色:账号，如 STUDENT:10001',
  MODIFY COLUMN `gender` VARCHAR(12) NOT NULL DEFAULT '保密' COMMENT '性别：保密、男、女',
  MODIFY COLUMN `birth_date` DATE NULL DEFAULT NULL COMMENT '出生日期',
  MODIFY COLUMN `school` VARCHAR(120) NOT NULL DEFAULT '' COMMENT '学校名称',
  MODIFY COLUMN `education` VARCHAR(40) NOT NULL DEFAULT '' COMMENT '学历',
  MODIFY COLUMN `recipient` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '收货人姓名',
  MODIFY COLUMN `phone` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '收货联系电话',
  MODIFY COLUMN `address` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '完整收货地址，含省市区、街道和门牌号';

ALTER TABLE `product_detail`
  COMMENT = '商品图文详情与发货地表',
  MODIFY COLUMN `ad_id` BIGINT NOT NULL COMMENT '商品ID，对应 advertisement.ad_id',
  MODIFY COLUMN `origin` VARCHAR(120) NOT NULL DEFAULT '' COMMENT '商品发货地',
  MODIFY COLUMN `blocks` MEDIUMTEXT NOT NULL COMMENT '图文段落JSON数组，包含文字段落和站内图片地址';

ALTER TABLE `community_user`
  COMMENT = '社区用户警告与禁言记录表',
  MODIFY COLUMN `owner` VARCHAR(90) NOT NULL COMMENT '用户唯一标识，格式为角色:账号',
  MODIFY COLUMN `warnings` INT NOT NULL DEFAULT 0 COMMENT '累计违规回复警告次数，超过3次触发社区禁言',
  MODIFY COLUMN `muted_until` DATETIME NULL DEFAULT NULL COMMENT '社区禁言截止时间，NULL表示未禁言';

ALTER TABLE `community_post`
  COMMENT = '社区帖子表',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '帖子ID，自动生成',
  MODIFY COLUMN `owner` VARCHAR(90) NOT NULL COMMENT '发帖用户唯一标识，格式为角色:账号',
  MODIFY COLUMN `author` VARCHAR(100) NOT NULL COMMENT '发布时的作者显示名称',
  MODIFY COLUMN `title` VARCHAR(120) NOT NULL COMMENT '帖子标题',
  MODIFY COLUMN `blocks` MEDIUMTEXT NOT NULL COMMENT '帖子图文段落JSON数组，包含文字和站内图片',
  MODIFY COLUMN `search_text` MEDIUMTEXT NOT NULL COMMENT '从图文中提取的纯文字，供搜索和监控词检测',
  MODIFY COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT '帖子状态：PUBLIC公开、HIDDEN作者隐藏、FORCED管理员强制隐藏、DELETED已删除',
  MODIFY COLUMN `featured` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否精华帖：0否、1是',
  MODIFY COLUMN `pinned` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否置顶帖：0否、1是',
  MODIFY COLUMN `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次发布时间，编辑或重新发布不刷新',
  MODIFY COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近编辑时间，由业务代码更新';

ALTER TABLE `community_reply`
  COMMENT = '社区帖子回复表',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '回复ID，自动生成',
  MODIFY COLUMN `post_id` BIGINT NOT NULL COMMENT '所属帖子ID，对应 community_post.id',
  MODIFY COLUMN `owner` VARCHAR(90) NOT NULL COMMENT '回复用户唯一标识，格式为角色:账号',
  MODIFY COLUMN `author` VARCHAR(100) NOT NULL COMMENT '回复时的作者显示名称',
  MODIFY COLUMN `content` TEXT NOT NULL COMMENT '回复正文，命中屏蔽词的部分以星号替换后保存',
  MODIFY COLUMN `parent_id` BIGINT NULL DEFAULT NULL COMMENT '被回复的回复ID，NULL表示直接回复帖子',
  MODIFY COLUMN `deleted` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记：0正常、1已删除',
  MODIFY COLUMN `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回复发布时间';

ALTER TABLE `community_view`
  COMMENT = '帖子每日浏览去重记录表',
  MODIFY COLUMN `post_id` BIGINT NOT NULL COMMENT '被浏览的帖子ID，对应 community_post.id',
  MODIFY COLUMN `owner` VARCHAR(90) NOT NULL COMMENT '浏览用户唯一标识，格式为角色:账号',
  MODIFY COLUMN `view_day` DATE NOT NULL COMMENT '浏览日期，同一账号每天对同一帖子仅计一次浏览';

ALTER TABLE `community_like`
  COMMENT = '社区帖子点赞记录表',
  MODIFY COLUMN `post_id` BIGINT NOT NULL COMMENT '被点赞的帖子ID，对应 community_post.id',
  MODIFY COLUMN `owner` VARCHAR(90) NOT NULL COMMENT '点赞用户唯一标识，同一账号对同一帖最多保留一条有效点赞';

ALTER TABLE `community_notice`
  COMMENT = '社区互动与管理通知表',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID，自动生成',
  MODIFY COLUMN `owner` VARCHAR(90) NOT NULL COMMENT '接收通知的用户唯一标识，格式为角色:账号',
  MODIFY COLUMN `post_id` BIGINT NULL DEFAULT NULL COMMENT '关联帖子ID，NULL表示无关联帖子',
  MODIFY COLUMN `kind` VARCHAR(30) NOT NULL COMMENT '通知类型：FEATURED精华、REPLY回复、LIKE点赞、FORCED下架、DELETED删帖、REPLY_DELETED删回复、WARNING警告',
  MODIFY COLUMN `message` VARCHAR(500) NOT NULL COMMENT '通知内容',
  MODIFY COLUMN `is_read` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已读：0未读、1已读',
  MODIFY COLUMN `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '通知创建时间';

ALTER TABLE `community_word`
  COMMENT = '社区帖子与回复专用监控词表',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '监控词ID，自动生成',
  MODIFY COLUMN `word` VARCHAR(80) NOT NULL COMMENT '监控词原文，唯一；发帖命中则拒发，回复命中则替换并警告';

ALTER TABLE `order_record`
  MODIFY COLUMN `received_time` DATETIME NULL DEFAULT NULL COMMENT '收货确认时间，满7天且未评价时自动生成默认好评',
  MODIFY COLUMN `shipping_snapshot` VARCHAR(700) NULL DEFAULT NULL COMMENT '下单时的收货人、电话及地址快照，后续修改个人地址不影响历史订单';



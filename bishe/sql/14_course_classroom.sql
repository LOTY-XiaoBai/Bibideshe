-- 保留旧课程和人员数据；按列检查，支持人工执行后再由启动迁移接管。

SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course' AND column_name='fixed_schedule'),'SELECT 1','ALTER TABLE course ADD COLUMN fixed_schedule TINYINT NOT NULL DEFAULT 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course' AND column_name='class_weekday'),'SELECT 1','ALTER TABLE course ADD COLUMN class_weekday TINYINT NULL');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course' AND column_name='class_time'),'SELECT 1','ALTER TABLE course ADD COLUMN class_time VARCHAR(5) NULL');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course' AND column_name='approval_required'),'SELECT 1','ALTER TABLE course ADD COLUMN approval_required TINYINT NOT NULL DEFAULT 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course' AND column_name='max_students'),'SELECT 1','ALTER TABLE course ADD COLUMN max_students INT NULL');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS course_application (
  course_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
  applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at DATETIME NULL,
  PRIMARY KEY(course_id, student_id),
  INDEX idx_application_student(student_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS course_session (
  session_id VARCHAR(36) NOT NULL PRIMARY KEY,
  course_id BIGINT NOT NULL,
  mode VARCHAR(10) NOT NULL,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  heartbeat_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at DATETIME NULL,
  INDEX idx_session_course(course_id, ended_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

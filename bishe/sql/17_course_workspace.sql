-- 课程页面扩展：保留既有课程、成员及成绩。
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course' AND column_name='cover_url'),'SELECT 1','ALTER TABLE course ADD COLUMN cover_url VARCHAR(255) NULL');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course' AND column_name='show_student_ranking'),'SELECT 1','ALTER TABLE course ADD COLUMN show_student_ranking TINYINT NOT NULL DEFAULT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course_personnel' AND column_name='joined_at'),'SELECT 1','ALTER TABLE course_personnel ADD COLUMN joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course_application' AND column_name='student_hidden'),'SELECT 1','ALTER TABLE course_application ADD COLUMN student_hidden TINYINT NOT NULL DEFAULT 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='course_personnel' AND index_name='idx_members_joined'),'SELECT 1','ALTER TABLE course_personnel ADD INDEX idx_members_joined(course_id,joined_at,student_id)');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS course_material (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 course_id BIGINT NOT NULL,
 teacher_id BIGINT NOT NULL,
 original_name VARCHAR(255) NOT NULL,
 storage_name VARCHAR(100) NOT NULL,
 file_size BIGINT NOT NULL,
 content_type VARCHAR(120) NOT NULL,
 uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_course_material_storage(storage_name),
 KEY idx_course_material(course_id,uploaded_at,id),
 CONSTRAINT fk_course_material FOREIGN KEY(course_id) REFERENCES course(course_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程资料元数据，文件存储在私有上传目录';

-- 预备小测与课堂小测：只新增结构，不改写已有课程、题库和成绩。
CREATE TABLE IF NOT EXISTS quiz_template (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 teacher_id BIGINT NOT NULL,
 name VARCHAR(100) NOT NULL,
 time_limit_minutes INT NULL,
 hide_answers TINYINT NOT NULL DEFAULT 0,
 count_score TINYINT NOT NULL DEFAULT 0,
 show_ranking TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_quiz_template_teacher(teacher_id,updated_at),
 CONSTRAINT fk_quiz_template_teacher FOREIGN KEY(teacher_id) REFERENCES teacher(teacher_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师命名的预备小测';

CREATE TABLE IF NOT EXISTS quiz_template_question (
 template_id BIGINT NOT NULL,
 position INT NOT NULL,
 question_id BIGINT NOT NULL,
 question_type TINYINT NOT NULL,
 score DECIMAL(8,2) NOT NULL,
 snapshot_json MEDIUMTEXT NOT NULL,
 PRIMARY KEY(template_id,question_type,question_id),
 UNIQUE KEY uk_quiz_template_position(template_id,position),
 CONSTRAINT fk_quiz_template_question FOREIGN KEY(template_id) REFERENCES quiz_template(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预备小测题目及答案快照';

CREATE TABLE IF NOT EXISTS classroom_quiz (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 template_id BIGINT NULL,
 course_id BIGINT NOT NULL,
 teacher_id BIGINT NOT NULL,
 session_id VARCHAR(36) NOT NULL,
 name VARCHAR(100) NOT NULL,
 mode VARCHAR(10) NOT NULL,
 hide_answers TINYINT NOT NULL DEFAULT 0,
 count_score TINYINT NOT NULL DEFAULT 0,
 show_ranking TINYINT NOT NULL DEFAULT 0,
 max_score DECIMAL(8,2) NOT NULL,
 started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deadline DATETIME NULL,
 ended_at DATETIME NULL,
 KEY idx_quiz_course(course_id,ended_at,id),
 KEY idx_quiz_session(session_id,ended_at),
 KEY idx_quiz_teacher(teacher_id,id),
 KEY idx_quiz_deadline(ended_at,deadline),
 CONSTRAINT fk_classroom_quiz_course FOREIGN KEY(course_id) REFERENCES course(course_id) ON DELETE CASCADE,
 CONSTRAINT fk_classroom_quiz_template FOREIGN KEY(template_id) REFERENCES quiz_template(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课堂已发布的小测，独立保留配置及成绩';

CREATE TABLE IF NOT EXISTS classroom_quiz_question (
 quiz_id BIGINT NOT NULL,
 position INT NOT NULL,
 question_id BIGINT NOT NULL,
 question_type TINYINT NOT NULL,
 score DECIMAL(8,2) NOT NULL,
 snapshot_json MEDIUMTEXT NOT NULL,
 PRIMARY KEY(quiz_id,question_type,question_id),
 UNIQUE KEY uk_classroom_quiz_position(quiz_id,position),
 CONSTRAINT fk_classroom_quiz_question FOREIGN KEY(quiz_id) REFERENCES classroom_quiz(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布时冻结的题目及参考答案';

CREATE TABLE IF NOT EXISTS classroom_quiz_student (
 quiz_id BIGINT NOT NULL,
 student_id BIGINT NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
 score DECIMAL(8,2) NOT NULL DEFAULT 0,
 graded TINYINT NOT NULL DEFAULT 0,
 submitted_at DATETIME NULL,
 PRIMARY KEY(quiz_id,student_id),
 KEY idx_quiz_student(student_id,status,quiz_id),
 CONSTRAINT fk_quiz_student_run FOREIGN KEY(quiz_id) REFERENCES classroom_quiz(id) ON DELETE CASCADE,
 CONSTRAINT fk_quiz_student_user FOREIGN KEY(student_id) REFERENCES student(student_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布时的学生名单和小测成绩';

CREATE TABLE IF NOT EXISTS classroom_quiz_answer (
 quiz_id BIGINT NOT NULL,
 student_id BIGINT NOT NULL,
 question_id BIGINT NOT NULL,
 question_type TINYINT NOT NULL,
 student_answer TEXT NOT NULL,
 score DECIMAL(8,2) NULL,
 annotation VARCHAR(500) NOT NULL DEFAULT '',
 PRIMARY KEY(quiz_id,student_id,question_type,question_id),
 CONSTRAINT fk_quiz_answer_student FOREIGN KEY(quiz_id,student_id) REFERENCES classroom_quiz_student(quiz_id,student_id) ON DELETE CASCADE,
 CONSTRAINT fk_quiz_answer_question FOREIGN KEY(quiz_id,question_type,question_id) REFERENCES classroom_quiz_question(quiz_id,question_type,question_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小测答卷，解答题支持人工批阅';

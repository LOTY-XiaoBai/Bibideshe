CREATE TABLE IF NOT EXISTS error_book_redo (
    redo_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    student_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    submitted_at DATETIME(6) NULL,
    questions_json JSON NOT NULL,
    result_json JSON NULL,
    INDEX idx_error_book_redo_history (student_id, submitted_at, redo_id),
    CONSTRAINT fk_error_book_redo_student FOREIGN KEY (student_id) REFERENCES student(student_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS error_book_deleted_question (
    question_id BIGINT NOT NULL,
    question_type TINYINT NOT NULL,
    snapshot_json JSON NOT NULL,
    PRIMARY KEY (question_id, question_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

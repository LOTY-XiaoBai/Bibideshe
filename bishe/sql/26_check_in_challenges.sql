CREATE TABLE IF NOT EXISTS check_in_challenge (
    check_in_id BIGINT NOT NULL PRIMARY KEY,
    mode VARCHAR(12) NOT NULL,
    configuration VARCHAR(256) NOT NULL,
    CONSTRAINT fk_check_in_challenge_record FOREIGN KEY (check_in_id)
        REFERENCES check_in_record(check_in_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

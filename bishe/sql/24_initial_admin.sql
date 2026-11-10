CREATE TABLE IF NOT EXISTS initial_admin_config (
    id TINYINT PRIMARY KEY,
    admin_id VARCHAR(20) NULL,
    amap_key_ciphertext VARCHAR(1024) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO initial_admin_config(id) VALUES (1);

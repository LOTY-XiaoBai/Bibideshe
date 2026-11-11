CREATE TABLE IF NOT EXISTS amap_credential (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(16) NOT NULL,
    label VARCHAR(80) NOT NULL,
    priority INT NOT NULL,
    key_ciphertext VARCHAR(512) NOT NULL,
    security_ciphertext VARCHAR(512),
    INDEX idx_amap_credential_order (type, priority, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

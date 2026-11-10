CREATE TABLE IF NOT EXISTS shop_product_tag (
    product_id BIGINT NOT NULL,
    tag_code VARCHAR(24) NOT NULL,
    PRIMARY KEY (product_id, tag_code),
    KEY idx_shop_product_tag (tag_code, product_id),
    CONSTRAINT fk_shop_product_tag_product FOREIGN KEY (product_id) REFERENCES advertisement(ad_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

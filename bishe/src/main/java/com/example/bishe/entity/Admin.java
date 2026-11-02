package com.example.bishe.entity;

import lombok.Data;

/**
 * 管理员账号实体
 * 对应数据库表 admin
 * 管理员ID格式为 "admin" + 5位随机数字, 例如 admin01234。
 *
 * 注意: 数据库中 admin_id 目前为 BIGINT, 无法存放 "admin" 前缀,
 * 需要将该列修改为 VARCHAR 类型, 例如:
 *   ALTER TABLE `admin` MODIFY COLUMN `admin_id` VARCHAR(20) NOT NULL COMMENT '管理员ID';
 */
@Data
public class Admin {

    /** 管理员ID("admin" + 5位随机数字) */
    private String adminId;

    /** 管理员密码 */
    private String password;
}

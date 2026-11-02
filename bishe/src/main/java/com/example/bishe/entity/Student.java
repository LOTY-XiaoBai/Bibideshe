package com.example.bishe.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生账号实体
 * 对应数据库表 student
 * 学生ID为 1 开头的随机数字。
 */
@Data
public class Student {

    /** 学生ID(1开头的随机数字) */
    private Long studentId;

    /** 学生昵称 */
    private String nickname;

    /** 学生密码 */
    private String password;

    /** 学生个性签名 */
    private String signature;

    /** 学生头像(URL) */
    private String avatar;

    /** 综合评分 */
    private BigDecimal comprehensiveScore;

    /** 软删除: 0正常, 1已注销 */
    private Integer isDeleted;

    /** 封禁: 0正常, 1已封禁 */
    private Integer isBanned;

    private String banReason;
    private String bannedBy;
    private LocalDateTime bannedAt;
}

package com.example.bishe.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 教师账号实体
 * 对应数据库表 teacher
 * 教师ID为 2 开头的随机数字。
 */
@Data
public class Teacher {

    /** 教师ID(2开头的随机数字) */
    private Long teacherId;

    /** 教师姓名 */
    private String name;

    /** 教师个性签名 */
    private String signature;

    /** 教师密码 */
    private String password;

    /** 教师头像(URL) */
    private String avatar;

    /** 综合评分 */
    private BigDecimal comprehensiveScore;

    /** 已有课程数量 */
    private Integer courseCount;

    /** 软删除: 0正常, 1已注销 */
    private Integer isDeleted;

    /** 封禁: 0正常, 1已封禁 */
    private Integer isBanned;

    private String banReason;
    private String bannedBy;
    private LocalDateTime bannedAt;
}

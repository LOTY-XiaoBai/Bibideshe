package com.example.bishe.dto;

import lombok.Data;

/**
 * 个人资料修改请求。字段为 null 表示不修改该项。
 * 学生可改昵称/头像/个性签名; 教师可改姓名/头像/个性签名。
 */
@Data
public class ProfileUpdateRequest {

    /** 学生昵称 / 教师姓名 */
    private String displayName;

    /** 个性签名 */
    private String signature;

    /** 头像URL(通常先调用 /api/file/upload/image 获得) */
    private String avatar;
}

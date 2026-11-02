package com.example.bishe.dto;

import lombok.Data;

/**
 * 教师手动修改学生签到状态请求。
 */
@Data
public class CheckInStatusUpdateRequest {

    /** 签到ID */
    private Long checkInId;

    /** 教师ID(用于权限校验) */
    private Long teacherId;

    /** 学生ID */
    private Long studentId;

    /** 目标状态: 0缺勤, 1已签到 */
    private Integer status;
}

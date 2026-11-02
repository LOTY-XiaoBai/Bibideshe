package com.example.bishe.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生签到明细实体 (表24 check_in_user)。
 */
@Data
public class CheckInUser {

    /** 签到ID */
    private Long checkInId;

    /** 学生ID */
    private Long studentId;

    /** 实际签到时间 */
    private LocalDateTime checkInTime;

    /** 签到状态: 0缺勤, 1已签到, 2迟到 */
    private Integer status;
}

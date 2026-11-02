package com.example.bishe.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 签到详情视图(教师查看某次签到的全体学生签到情况)。
 */
@Data
public class CheckInDetailVO {

    /** 签到ID */
    private Long checkInId;

    /** 签到发布时间 */
    private LocalDateTime publishTime;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 实际结束时间 */
    private LocalDateTime endTime;

    /** 签到状态: 0进行中, 1已结束 */
    private Integer checkInStatus;

    /** 学生ID */
    private Long studentId;

    /** 学生昵称 */
    private String studentNickname;

    /** 学生签到时间 */
    private LocalDateTime checkInTime;

    /** 学生签到状态: 0缺勤, 1已签到, 2迟到 */
    private Integer studentStatus;
}

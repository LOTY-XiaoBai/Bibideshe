package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 签到记录概览视图(教师查看课程所有签到记录)。
 */
@Data
public class CheckInRecordVO {
    private String mode;

    /** 签到ID */
    private Long checkInId;

    /** 课程ID */
    private Long courseId;

    /** 课程名称 */
    private String courseName;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 实际结束时间 */
    private LocalDateTime endTime;

    /** 签到人数 */
    private Integer attendeeCount;

    /** 签到状态: 0进行中, 1已结束 */
    private Integer status;

    /** 本次签到分值 */
    private BigDecimal scoreValue;
}

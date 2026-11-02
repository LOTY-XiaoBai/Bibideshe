package com.example.bishe.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 签到发布记录实体 (表23 check_in_record)。
 */
@Data
public class CheckInRecord {

    /** 签到ID(自增) */
    private Long checkInId;

    /** 课程ID */
    private Long courseId;

    /** 发布者ID(教师ID) */
    private Long publisherId;

    /** 签到发布时间 */
    private LocalDateTime publishTime;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 实际结束时间(教师手动结束或自动到期) */
    private LocalDateTime endTime;

    /** 签到人数 */
    private Integer attendeeCount;

    /** 签到状态: 0进行中, 1已结束 */
    private Integer status;

    /** 本次签到分值(计入课程签到得分) */
    private BigDecimal scoreValue;
}

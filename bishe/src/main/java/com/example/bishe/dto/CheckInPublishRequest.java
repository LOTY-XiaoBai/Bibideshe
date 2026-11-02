package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 教师发布签到请求。
 */
@Data
public class CheckInPublishRequest {

    /** 教师ID */
    private Long teacherId;

    /** 课程ID */
    private Long courseId;

    /** 签到截止时间 */
    private LocalDateTime deadline;

    /** 本次签到分值(必填, 计入课程总成绩的签到部分) */
    private BigDecimal scoreValue;
}

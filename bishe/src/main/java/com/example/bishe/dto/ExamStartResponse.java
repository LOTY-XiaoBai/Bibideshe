package com.example.bishe.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生开始考试后的会话信息(含截止时间)。
 */
@Data
public class ExamStartResponse {

    private Long examId;

    private Long studentId;

    /** 开始作答时间 */
    private LocalDateTime startTime;

    /** 限制时长(分钟) */
    private Integer timeLimit;

    /** 最晚提交截止时间 = startTime + timeLimit */
    private LocalDateTime deadline;
}

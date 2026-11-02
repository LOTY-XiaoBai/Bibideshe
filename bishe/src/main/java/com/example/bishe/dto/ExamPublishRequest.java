package com.example.bishe.dto;

import lombok.Data;

import java.util.List;

/**
 * 教师发布考试请求。
 */
@Data
public class ExamPublishRequest {

    /** 课程ID */
    private Long courseId;

    /** 发布者ID(教师ID, 用于校验课程归属) */
    private Long teacherId;

    /** 考试限制时间(分钟) */
    private Integer timeLimit;

    /** 组卷的题目与分值列表 */
    private List<QuestionScoreItem> questions;

    /**
     * 是否禁止截止前/提交后立即查看成绩与答案(需教师手动公布)。
     * 1=需教师公布后才可查看, 0=提交后可立即复盘(默认)。
     */
    private Integer hideResultUntilPublish;
}

package com.example.bishe.entity;

import lombok.Data;

/**
 * 考试实体 (表14 exam)。
 */
@Data
public class Exam {

    /** 考试ID(自增) */
    private Long examId;

    /** 课程ID */
    private Long courseId;

    /** 发布者ID(教师ID) */
    private Long publisherId;

    /** 考试限制时间(分钟) */
    private Integer timeLimit;

    /** 是否需教师公布后才可查看成绩/答案: 1是 0否 */
    private Integer hideResultUntilPublish;

    /** 教师是否已公布成绩/答案: 1是 0否 */
    private Integer isResultPublished;
}

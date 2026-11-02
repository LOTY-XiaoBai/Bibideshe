package com.example.bishe.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业实体 (表17 homework)。
 */
@Data
public class Homework {

    /** 作业ID(自增) */
    private Long homeworkId;

    /** 课程ID */
    private Long courseId;

    /** 发布者ID(教师ID) */
    private Long publisherId;

    /** 截至时间 */
    private LocalDateTime deadline;

    /** 是否需教师公布后才可查看成绩/答案: 1是 0否 */
    private Integer hideResultUntilPublish;

    /** 教师是否已公布成绩/答案: 1是 0否 */
    private Integer isResultPublished;
}

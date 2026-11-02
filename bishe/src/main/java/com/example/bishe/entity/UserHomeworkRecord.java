package com.example.bishe.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户作业总记录实体 (表19 user_homework_record)。
 */
@Data
public class UserHomeworkRecord {

    /** 学生ID */
    private Long studentId;

    /** 作业ID */
    private Long homeworkId;

    /** 作业得分(按题目分数汇总的自动得分) */
    private BigDecimal homeworkScore;

    /** 教师评分 */
    private BigDecimal teacherScore;
}

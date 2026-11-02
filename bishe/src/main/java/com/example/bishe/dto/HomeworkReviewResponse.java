package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学生作业作答复盘/提交结果响应。
 */
@Data
public class HomeworkReviewResponse {

    /** 作业ID */
    private Long homeworkId;

    /** 学生ID */
    private Long studentId;

    /** 作业自动得分(题目分数汇总) */
    private BigDecimal homeworkScore;

    /** 教师评分 */
    private BigDecimal teacherScore;

    /** 每道题的作答详情 */
    private List<AnswerReviewItem> answers;
}

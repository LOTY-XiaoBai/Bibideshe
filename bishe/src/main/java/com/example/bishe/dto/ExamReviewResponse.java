package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学生考试作答复盘/提交结果响应。
 */
@Data
public class ExamReviewResponse {

    /** 考试ID */
    private Long examId;

    /** 学生ID */
    private Long studentId;

    /** 考试自动得分(题目分数汇总) */
    private BigDecimal examScore;

    /** 教师评分 */
    private BigDecimal teacherScore;

    /** 每道题的作答详情 */
    private List<AnswerReviewItem> answers;
}

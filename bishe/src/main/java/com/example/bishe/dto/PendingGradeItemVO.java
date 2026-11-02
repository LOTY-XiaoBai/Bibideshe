package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 待人工批改的解答题条目。
 */
@Data
public class PendingGradeItemVO {

    private Long examId;
    private Long homeworkId;
    private Long questionId;
    private Integer questionType;
    private Long studentId;
    private Long courseId;
    private String studentAnswer;
    private BigDecimal fullScore;
    private String annotation;
    private QuestionView question;
}

package com.example.bishe.dto;

import lombok.Data;

import java.util.List;

/**
 * 学生提交考试请求。
 */
@Data
public class ExamSubmitRequest {

    /** 考试ID */
    private Long examId;

    /** 学生ID */
    private Long studentId;

    /** 各题作答内容 */
    private List<AnswerItem> answers;
}

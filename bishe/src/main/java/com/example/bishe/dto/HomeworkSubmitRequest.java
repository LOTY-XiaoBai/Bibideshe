package com.example.bishe.dto;

import lombok.Data;

import java.util.List;

/**
 * 学生提交作业请求。
 */
@Data
public class HomeworkSubmitRequest {

    /** 作业ID */
    private Long homeworkId;

    /** 学生ID */
    private Long studentId;

    /** 各题作答内容 */
    private List<AnswerItem> answers;
}

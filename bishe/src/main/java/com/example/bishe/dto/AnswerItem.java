package com.example.bishe.dto;

import lombok.Data;

/**
 * 学生提交的一道题的答案(用于考试/作业提交请求中的答案列表)。
 */
@Data
public class AnswerItem {

    /** 题目ID */
    private Long questionId;

    /** 题目类型: 1选择题, 2判断题, 3解答题 */
    private Integer questionType;

    /** 学生答案 */
    private String studentAnswer;
}

package com.example.bishe.dto;

import lombok.Data;

/**
 * 管理员删除题目请求(无论题目是否已上传至公共题库均可删除)。
 */
@Data
public class QuestionDeleteRequest {

    /** 题目ID */
    private Long questionId;

    /** 题目类型: 1选择题, 2判断题, 3解答题 */
    private Integer questionType;
}

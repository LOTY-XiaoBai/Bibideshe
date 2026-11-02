package com.example.bishe.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题本实体 (表25 error_book)。
 */
@Data
public class ErrorBook {

    /** 学生ID */
    private Long studentId;

    /** 题目ID */
    private Long questionId;

    /** 题目类型: 1选择题, 2判断题, 3解答题 */
    private Integer questionType;

    /** 加入时间 */
    private LocalDateTime addTime;
}

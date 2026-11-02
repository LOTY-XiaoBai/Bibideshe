package com.example.bishe.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单评价实体 (表10.1 order_evaluation)。
 */
@Data
public class OrderEvaluation {

    private Long evaluationId;

    private Long orderId;

    /** 买家ID(学生/教师) */
    private Long buyerId;

    /** 评价星级 1-5 */
    private Integer rating;

    /** 文字反馈 */
    private String feedbackContent;

    private LocalDateTime publishTime;
}

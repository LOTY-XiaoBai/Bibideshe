package com.example.bishe.dto;

import lombok.Data;

/**
 * 订单评价/反馈请求。
 */
@Data
public class OrderFeedbackRequest {

    private Long orderId;

    /** 星级 1-5 */
    private Integer rating;

    private String feedbackContent;
}

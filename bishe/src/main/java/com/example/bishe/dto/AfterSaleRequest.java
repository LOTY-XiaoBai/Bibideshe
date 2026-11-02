package com.example.bishe.dto;

import lombok.Data;

/**
 * 售后申请请求。
 */
@Data
public class AfterSaleRequest {

    private Long orderId;

    private String content;
}

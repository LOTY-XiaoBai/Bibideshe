package com.example.bishe.dto;

import lombok.Data;

/**
 * 管理员处理售后请求。
 */
@Data
public class AfterSaleReplyRequest {

    private Long orderId;

    private String reply;
}

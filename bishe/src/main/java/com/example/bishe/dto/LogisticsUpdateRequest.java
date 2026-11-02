package com.example.bishe.dto;

import lombok.Data;

/**
 * 发布物流信息请求。
 */
@Data
public class LogisticsUpdateRequest {

    private Long orderId;

    private String logisticsInfo;
}

package com.example.bishe.dto;

import com.example.bishe.entity.Advertisement;
import com.example.bishe.entity.OrderEvaluation;
import com.example.bishe.entity.OrderRecord;
import lombok.Data;

/**
 * 订单详情视图(含广告与评价)。
 */
@Data
public class OrderVO {

    private OrderRecord order;

    private Advertisement advertisement;

    private UserProfileVO buyer;

    private OrderEvaluation evaluation;
}

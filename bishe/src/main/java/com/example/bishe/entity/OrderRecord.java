package com.example.bishe.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品订单实体 (表10 order_record)。
 * buyer_id 可为学生或教师。
 */
@Data
public class OrderRecord {

    private Long orderId;
    private Long merchantId;
    private String productTitle;
    private java.math.BigDecimal unitPrice;
    private String shipOrigin;
    private String shippingSnapshot;
    private LocalDateTime receivedTime;

    private Long adId;

    /** 买家ID(学生/教师) */
    private Long buyerId;

    /** 是否收货: 0否, 1是 */
    private Integer isReceived;

    /** 物流信息 */
    private String logisticsInfo;

    private LocalDateTime createTime;

    /**
     * 订单状态: 0待发货, 1已发货, 2已收货
     */
    private Integer status;

    /** 售后申请内容 */
    private String afterSaleContent;

    /** 售后状态: 0无, 1申请中, 2已处理 */
    private Integer afterSaleStatus;

    private LocalDateTime afterSaleTime;

    /** 管理员售后回复 */
    private String afterSaleReply;
}

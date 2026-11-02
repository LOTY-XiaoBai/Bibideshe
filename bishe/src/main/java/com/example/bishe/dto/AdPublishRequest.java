package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理员发布/更新广告(商品)请求。
 */
@Data
public class AdPublishRequest {

    /** 更新时必填 */
    private Long adId;

    private String title;

    private String content;

    private String imageUrl;

    private BigDecimal price;
}

package com.example.bishe.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品实体，沿用 advertisement 表及 adId 以兼容历史订单。
 * 首页广告独立保存在 merchant_campaign，商品需审核通过后才能浏览下单。
 */
@Data
public class Advertisement {

    private Long adId;

    /** 商品/广告标题 */
    private String title;

    /** 广告内容描述 */
    private String content;

    /** 广告图片URL */
    private String imageUrl;

    /** 商品价格 */
    private BigDecimal price;

    /** 发布者账号；新商品为16位商家账号 */
    private String publisherId;

    /** 综合评分(评价均值) */
    private BigDecimal score;

    private LocalDateTime createTime;

    /** 软删除/下架: 0正常, 1已下架 */
    private Integer isDeleted;
    private Long merchantId;
    private String merchantName;
    private String reviewStatus;
    private String shipCity;
    @JsonIgnore
    private String tagCodes;

    public List<String> getTags() {
        return tagCodes == null || tagCodes.isBlank() ? List.of() : List.of(tagCodes.split(","));
    }
}

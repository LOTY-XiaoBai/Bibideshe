package com.example.bishe.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 广告/商品实体 (表4 advertisement)。
 * 广告即商品展示页, 师生均可浏览与下单。
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

    /** 发布者(管理员ID) */
    private String publisherId;

    /** 综合评分(评价均值) */
    private BigDecimal score;

    private LocalDateTime createTime;

    /** 软删除/下架: 0正常, 1已下架 */
    private Integer isDeleted;
}

package com.example.bishe.dto;

import lombok.Data;

/**
 * 特殊词增删请求。
 */
@Data
public class SpecialWordRequest {

    private Long wordId;
    private String word;
    private Integer enabled;
}

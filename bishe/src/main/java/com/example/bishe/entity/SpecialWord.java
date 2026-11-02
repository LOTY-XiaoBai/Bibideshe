package com.example.bishe.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 特殊词实体 (special_word)。
 */
@Data
public class SpecialWord {

    private Long wordId;
    private String word;
    private Integer enabled;
    private LocalDateTime createTime;
    private String createdBy;
}

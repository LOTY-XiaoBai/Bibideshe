package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 班级排名条目: 他人不返回具体分数。
 */
@Data
public class ClassRankItemVO {

    private Integer rank;

    private Long studentId;

    private String nickname;

    private String avatar;

    private Boolean isMe;

    /** 仅本人可见自己的分数 */
    private BigDecimal score;
}

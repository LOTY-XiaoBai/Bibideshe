package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 考试/作业成绩排名条目。
 */
@Data
public class ScoreRankItemVO {

    private Integer rank;
    private Long studentId;
    private String nickname;
    private String avatar;
    /** 自动得分 */
    private BigDecimal autoScore;
    /** 教师评分(最终展示分优先) */
    private BigDecimal teacherScore;
    /** 排名用最终分 */
    private BigDecimal finalScore;
}

package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 班级成绩排名(不展示其他学生具体分数)。
 */
@Data
public class ClassRankVO {

    private Long courseId;

    private String courseName;

    private Integer myRank;

    private Integer totalStudents;

    private BigDecimal myScore;

    private List<ClassRankItemVO> ranking;
}

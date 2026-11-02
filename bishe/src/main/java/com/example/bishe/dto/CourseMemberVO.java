package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程班级成员视图(含学生昵称)。
 */
@Data
public class CourseMemberVO {

    private Long courseId;

    private Long studentId;

    private String nickname;

    private BigDecimal studentScore;
}

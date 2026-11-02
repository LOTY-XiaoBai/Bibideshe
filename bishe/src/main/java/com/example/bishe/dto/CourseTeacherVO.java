package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程中的教师评分视图(学生点进课程看老师时用)。
 */
@Data
public class CourseTeacherVO {

    private Long courseId;

    private String courseName;

    private Integer isFinished;

    private Long teacherId;

    private String teacherName;

    private String teacherAvatar;

    private String teacherSignature;

    /**
     * 教师综合评分。无人打分时为 null。
     */
    private BigDecimal comprehensiveScore;

    private Boolean hasRating;

    /** 无人打分时为「暂未有人打分」 */
    private String scoreTip;

    /** 当前学生对这位教师的已打分数, 未打过则为 null */
    private BigDecimal myScore;
}

package com.example.bishe.mapper;

import lombok.Data;

/**
 * 教师评分条数聚合结果 (countGroupedByTeacherIds)。
 */
@Data
public class TeacherRatingCount {

    private Long teacherId;

    private Integer ratingCount;
}

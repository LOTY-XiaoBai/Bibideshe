package com.example.bishe.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生签到记录视图。
 */
@Data
public class CheckInStudentRecordVO {

    /** 签到ID */
    private Long checkInId;

    /** 课程ID */
    private Long courseId;

    /** 课程名称 */
    private String courseName;

    /** 签到发布时间 */
    private LocalDateTime publishTime;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 学生签到时间 */
    private LocalDateTime checkInTime;

    /** 签到状态: 0缺勤, 1已签到, 2迟到 */
    private Integer status;

    /** 签到是否已结束 */
    private Integer checkInStatus;
}

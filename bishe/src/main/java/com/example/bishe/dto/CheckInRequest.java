package com.example.bishe.dto;

import lombok.Data;

/**
 * 学生签到请求。
 */
@Data
public class CheckInRequest {

    /** 签到ID */
    private Long checkInId;

    /** 学生ID */
    private Long studentId;
}

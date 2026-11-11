package com.example.bishe.dto;

import lombok.Data;
import java.util.List;

/**
 * 学生签到请求。
 */
@Data
public class CheckInRequest {
    public record Position(Double latitude, Double longitude, Double accuracy) {}

    /** 签到ID */
    private Long checkInId;

    /** 学生ID */
    private Long studentId;
    private String code;
    private List<Integer> gesture;
    private Position position;
}

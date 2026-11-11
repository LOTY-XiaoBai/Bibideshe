package com.example.bishe.dto;

import com.example.bishe.common.BusinessException;

public record CheckInBounds(Double west, Double south, Double east, Double north) {
    public String encode() {
        if (west == null || east == null || south == null || north == null
                || !(west >= -180 && east <= 180 && west < east && south >= -90 && north <= 90 && south < north)) {
            throw new BusinessException("请在地图上绘制有效的签到范围");
        }
        return west + "," + south + "," + east + "," + north;
    }

    public static CheckInBounds decode(String value) {
        String[] edges = value.split(",");
        return new CheckInBounds(Double.valueOf(edges[0]), Double.valueOf(edges[1]),
                Double.valueOf(edges[2]), Double.valueOf(edges[3]));
    }

    public boolean contains(double latitude, double longitude) {
        return latitude >= south && latitude <= north && longitude >= west && longitude <= east;
    }
}

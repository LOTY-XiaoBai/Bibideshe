package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.BusinessException;
import com.example.bishe.common.Result;
import com.example.bishe.service.MerchantLocationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/location")
public class LocationController {
    public record Coordinates(Double latitude, Double longitude) {
        void validate() {
            if (latitude == null || longitude == null) throw new BusinessException("定位坐标无效");
        }
    }

    private final MerchantLocationService location;

    public LocationController(MerchantLocationService location) {
        this.location = location;
    }

    @PostMapping("/address")
    public Result<Map<String, String>> address(@RequestBody Coordinates coordinates) {
        AuthContext.requireStudentId();
        coordinates.validate();
        return Result.success(Map.of("address", location.address(coordinates.latitude(), coordinates.longitude())));
    }

    @PostMapping("/map-position")
    public Result<MerchantLocationService.Point> mapPosition(@RequestBody Coordinates coordinates) {
        AuthContext.requireTeacherId();
        coordinates.validate();
        return Result.success(location.mapPosition(coordinates.latitude(), coordinates.longitude()));
    }
}

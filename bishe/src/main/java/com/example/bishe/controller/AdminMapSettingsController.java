package com.example.bishe.controller;

import com.example.bishe.common.Result;
import com.example.bishe.service.AdminMapSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/map-settings")
public class AdminMapSettingsController {
    public record Update(String key) {}

    private final AdminMapSettingsService settings;

    public AdminMapSettingsController(AdminMapSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public Result<Map<String, Object>> status() {
        return Result.success(settings.status());
    }

    @PutMapping
    public Result<Map<String, Object>> save(@RequestBody Update update) {
        return Result.success(settings.save(update.key()));
    }
}

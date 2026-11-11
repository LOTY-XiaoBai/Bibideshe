package com.example.bishe.controller;

import com.example.bishe.common.Result;
import com.example.bishe.service.AdminMapSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/admin/map-settings")
public class AdminMapSettingsController {
    public record Update(String key) {}
    public record Order(List<Long> ids) {}

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

    @PostMapping("/credentials")
    public Result<Map<String, Object>> add(@RequestBody AdminMapSettingsService.CredentialInput credential) {
        return Result.success(settings.addCredential(credential));
    }

    @PutMapping("/credentials/{type}/order")
    public Result<Map<String, Object>> order(@PathVariable String type, @RequestBody Order order) {
        return Result.success(settings.orderCredentials(type, order.ids()));
    }

    @DeleteMapping("/credentials/{id}")
    public Result<Map<String, Object>> delete(@PathVariable long id) {
        return Result.success(settings.deleteCredential(id));
    }
}

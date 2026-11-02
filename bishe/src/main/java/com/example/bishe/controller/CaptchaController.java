package com.example.bishe.controller;

import com.example.bishe.common.Result;
import com.example.bishe.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 图形验证码接口(登录/注册前获取)。
 */
@Tag(name = "验证码", description = "人机验证图形验证码")
@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @Operation(summary = "获取图形验证码")
    @GetMapping
    public Result<Map<String, String>> create() {
        return Result.success(captchaService.create());
    }
}

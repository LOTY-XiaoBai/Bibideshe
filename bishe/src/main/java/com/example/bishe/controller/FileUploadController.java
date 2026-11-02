package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传接口(头像/群头像/聊天图片与附件)。
 * 需登录; 返回相对 URL, 前端可拼接域名后用于资料修改或聊天发送。
 */
@Tag(name = "文件上传", description = "头像/群头像/聊天图片与附件上传")
@RestController
@RequestMapping("/api/file")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    /**
     * 上传图片。用于头像、群头像、聊天图片。
     * 返回: { "url": "/uploads/image/xxx.png" }
     */
    @Operation(summary = "上传图片")
    @PostMapping("/upload/image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        AuthContext.requireLogin();
        String url = fileUploadService.uploadImage(file);
        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return Result.success("上传成功", data);
    }

    /**
     * 上传通用文件。用于聊天附件等。
     */
    @Operation(summary = "上传通用文件")
    @PostMapping("/upload")
    public Result<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        AuthContext.requireLogin();
        String url = fileUploadService.uploadFile(file);
        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return Result.success("上传成功", data);
    }
}

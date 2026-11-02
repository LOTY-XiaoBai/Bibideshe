package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.LoginRequest;
import com.example.bishe.dto.LoginResponse;
import com.example.bishe.dto.MyProfileVO;
import com.example.bishe.dto.ProfileUpdateRequest;
import com.example.bishe.dto.TeacherRegisterRequest;
import com.example.bishe.entity.Teacher;
import com.example.bishe.service.TeacherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师账号相关接口。
 */
@Tag(name = "教师", description = "教师账号注册、登录与个人资料")
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    /**
     * 教师注册。
     */
    @Operation(summary = "教师注册")
    @PostMapping("/register")
    public Result<Teacher> register(@RequestBody TeacherRegisterRequest request) {
        return Result.success("注册成功", teacherService.register(request));
    }

    /**
     * 教师登录, 返回 Token。
     */
    @Operation(summary = "教师登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success("登录成功", teacherService.login(request));
    }

    /**
     * 查看当前登录教师的个人资料。无人打分时显示「暂未有人打分」。
     */
    @Operation(summary = "查看当前登录教师的个人资料")
    @GetMapping("/profile")
    public Result<MyProfileVO> profile() {
        Long teacherId = AuthContext.requireTeacherId();
        return Result.success(teacherService.getProfile(teacherId));
    }

    /**
     * 修改当前登录教师的个人资料(姓名/个性签名/头像)。
     */
    @Operation(summary = "修改当前登录教师的个人资料")
    @PutMapping("/profile")
    public Result<MyProfileVO> updateProfile(@RequestBody ProfileUpdateRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        return Result.success("资料已更新", teacherService.updateProfile(teacherId, request));
    }
}

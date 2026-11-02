package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.LoginRequest;
import com.example.bishe.dto.LoginResponse;
import com.example.bishe.dto.MyProfileVO;
import com.example.bishe.dto.ProfileUpdateRequest;
import com.example.bishe.dto.StudentRegisterRequest;
import com.example.bishe.entity.Student;
import com.example.bishe.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生账号相关接口。
 */
@Tag(name = "学生", description = "学生账号注册、登录与个人资料")
@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    /**
     * 学生注册。
     */
    @Operation(summary = "学生注册")
    @PostMapping("/register")
    public Result<Student> register(@RequestBody StudentRegisterRequest request) {
        return Result.success("注册成功", studentService.register(request));
    }

    /**
     * 学生登录, 返回 Token(后续请求 Header: Authorization: Bearer {token})。
     */
    @Operation(summary = "学生登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success("登录成功", studentService.login(request));
    }

    /**
     * 查看当前登录学生的个人资料。
     */
    @Operation(summary = "查看当前登录学生的个人资料")
    @GetMapping("/profile")
    public Result<MyProfileVO> profile() {
        Long studentId = AuthContext.requireStudentId();
        return Result.success(studentService.getProfile(studentId));
    }

    /**
     * 修改当前登录学生的个人资料(昵称/个性签名/头像)。
     */
    @Operation(summary = "修改当前登录学生的个人资料")
    @PutMapping("/profile")
    public Result<MyProfileVO> updateProfile(@RequestBody ProfileUpdateRequest request) {
        Long studentId = AuthContext.requireStudentId();
        return Result.success("资料已更新", studentService.updateProfile(studentId, request));
    }
}

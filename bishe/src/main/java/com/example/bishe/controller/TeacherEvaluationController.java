package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.CourseTeacherVO;
import com.example.bishe.dto.TeacherRateRequest;
import com.example.bishe.dto.TeacherScoreVO;
import com.example.bishe.service.TeacherEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教师评分接口(学生端)。
 */
@Tag(name = "教师评分", description = "学生对课程教师打分与查看评分")
@RestController
@RequestMapping("/api/teacher-evaluation")
public class TeacherEvaluationController {

    private final TeacherEvaluationService teacherEvaluationService;

    public TeacherEvaluationController(TeacherEvaluationService teacherEvaluationService) {
        this.teacherEvaluationService = teacherEvaluationService;
    }

    /**
     * 学生给课程教师打分(1-10)。只能给自己选修课程的主讲教师打分。
     */
    @Operation(summary = "学生给课程教师打分")
    @PostMapping("/rate")
    public Result<TeacherScoreVO> rate(@RequestBody TeacherRateRequest request) {
        Long studentId = AuthContext.requireStudentId();
        return Result.success("评分成功", teacherEvaluationService.rateTeacher(studentId, request));
    }

    /**
     * 查看某门已选课程的教师信息与评分(结课/未结课均可)。
     */
    @Operation(summary = "查看某门已选课程的教师信息与评分")
    @GetMapping("/course/{courseId}")
    public Result<CourseTeacherVO> courseTeacher(@PathVariable Long courseId) {
        Long studentId = AuthContext.requireStudentId();
        return Result.success(teacherEvaluationService.getCourseTeacher(studentId, courseId));
    }

    /**
     * 列出当前学生可打分的课程教师列表。
     */
    @Operation(summary = "列出当前学生可打分的课程教师列表")
    @GetMapping("/rateable")
    public Result<List<CourseTeacherVO>> rateable() {
        Long studentId = AuthContext.requireStudentId();
        return Result.success(teacherEvaluationService.listRateableTeachers(studentId));
    }

    /**
     * 查看某教师综合评分。无人打分时返回 scoreTip=暂未有人打分。
     */
    @Operation(summary = "查看某教师综合评分")
    @GetMapping("/score/{teacherId}")
    public Result<TeacherScoreVO> score(@PathVariable Long teacherId) {
        AuthContext.requireLogin();
        return Result.success(teacherEvaluationService.getTeacherScore(teacherId));
    }
}

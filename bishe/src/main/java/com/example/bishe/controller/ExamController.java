package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.AuthUser;
import com.example.bishe.common.Result;
import com.example.bishe.dto.ExamDetailResponse;
import com.example.bishe.dto.ExamPublishRequest;
import com.example.bishe.dto.ExamReviewResponse;
import com.example.bishe.dto.ExamStartResponse;
import com.example.bishe.dto.ExamSubmitRequest;
import com.example.bishe.dto.GradeAnswerRequest;
import com.example.bishe.dto.PendingGradeItemVO;
import com.example.bishe.dto.ScoreRankItemVO;
import com.example.bishe.entity.Exam;
import com.example.bishe.entity.UserExamRecord;
import com.example.bishe.service.ExamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 考试相关接口(教师端: 发布考试/批改解答题; 学生端: 参加并提交考试)。
 */
@Tag(name = "考试", description = "教师发布考试与批改、学生参加考试")
@RestController
@RequestMapping("/api/exam")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    // ==================== 教师端 ====================

    /** 教师发布考试(组卷: 选定题目ID列表与各题分值) */
    @Operation(summary = "教师发布考试")
    @PostMapping("/publish")
    public Result<Exam> publish(@RequestBody ExamPublishRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        request.setTeacherId(teacherId);
        return Result.success("考试发布成功", examService.publishExam(request));
    }

    /** 查询当前用户有权访问的某课程下全部考试 */
    @Operation(summary = "查询某课程下的全部考试")
    @GetMapping("/course/{courseId}")
    public Result<List<Exam>> byCourse(@PathVariable Long courseId) {
        AuthUser user = AuthContext.requireLogin();
        return Result.success(examService.listByCourse(courseId, user));
    }

    /** 查询当前教师发布的全部考试 */
    @Operation(summary = "查询当前教师发布的全部考试")
    @GetMapping("/teacher/{teacherId}")
    public Result<List<Exam>> byTeacher(@PathVariable Long teacherId) {
        Long selfId = AuthContext.requireSelfTeacherOrAdmin(teacherId);
        return Result.success(examService.listByTeacher(selfId));
    }

    /** 查询某次考试待人工批改的解答题列表 */
    @Operation(summary = "查询某次考试待人工批改的解答题列表")
    @GetMapping("/{examId}/pending-grade")
    public Result<List<PendingGradeItemVO>> pendingGrade(@PathVariable Long examId) {
        Long teacherId = AuthContext.requireTeacherId();
        return Result.success(examService.listPendingGrade(teacherId, examId));
    }

    /** 教师人工批改解答题 */
    @Operation(summary = "教师人工批改解答题")
    @PostMapping("/grade")
    public Result<Void> grade(@RequestBody GradeAnswerRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        examService.gradeSolveAnswer(teacherId, request);
        return Result.success("批改成功", null);
    }

    /** 教师公布考试成绩与答案 */
    @Operation(summary = "教师公布考试成绩与答案")
    @PostMapping("/{examId}/publish-result")
    public Result<Void> publishResult(@PathVariable Long examId) {
        Long teacherId = AuthContext.requireTeacherId();
        examService.publishResult(teacherId, examId);
        return Result.success("考试成绩已公布", null);
    }

    /** 教师查看考试成绩排名 */
    @Operation(summary = "教师查看考试成绩排名")
    @GetMapping("/{examId}/scores")
    public Result<List<ScoreRankItemVO>> scores(@PathVariable Long examId) {
        Long teacherId = AuthContext.requireTeacherId();
        return Result.success(examService.listScoreRanking(teacherId, examId));
    }

    /** 教师查看指定学生考试作答 */
    @Operation(summary = "教师查看指定学生考试作答")
    @GetMapping("/{examId}/student/{studentId}/review")
    public Result<ExamReviewResponse> teacherReview(@PathVariable Long examId,
                                                     @PathVariable Long studentId) {
        Long teacherId = AuthContext.requireTeacherId();
        return Result.success(examService.reviewExamForTeacher(teacherId, examId, studentId));
    }

    // ==================== 学生端 ====================

    /** 查询考试详情。答案是否可见完全由服务端根据当前角色决定。 */
    @Operation(summary = "查询考试详情")
    @GetMapping("/{examId}")
    public Result<ExamDetailResponse> detail(@PathVariable Long examId) {
        AuthUser user = AuthContext.requireLogin();
        return Result.success(examService.getExamDetail(examId, user));
    }

    /** 学生开始考试(记录开始时间, 提交须在限时内完成) */
    @Operation(summary = "学生开始考试")
    @PostMapping("/{examId}/start")
    public Result<ExamStartResponse> start(@PathVariable Long examId) {
        Long studentId = AuthContext.requireStudentId();
        return Result.success("考试已开始", examService.startExam(studentId, examId));
    }

    /** 学生提交考试答案, 选择题/判断题自动批改, 解答题进入待批改 */
    @Operation(summary = "学生提交考试答案")
    @PostMapping("/submit")
    public Result<ExamReviewResponse> submit(@RequestBody ExamSubmitRequest request) {
        Long studentId = AuthContext.requireStudentId();
        request.setStudentId(studentId);
        return Result.success("考试提交成功", examService.submitExam(request));
    }

    /** 学生查看自己某次考试的作答复盘 */
    @Operation(summary = "学生查看某次考试的作答复盘")
    @GetMapping("/{examId}/review")
    public Result<ExamReviewResponse> review(@PathVariable Long examId,
                                              @RequestParam(required = false) Long studentId) {
        Long selfId = studentId == null
                ? AuthContext.requireStudentId()
                : AuthContext.requireSelfStudentOrAdmin(studentId);
        return Result.success(examService.reviewExam(examId, selfId));
    }

    /** 查询某学生的全部考试记录(总分列表) */
    @Operation(summary = "查询某学生的全部考试记录")
    @GetMapping("/student/{studentId}/records")
    public Result<List<UserExamRecord>> studentRecords(@PathVariable Long studentId) {
        Long selfId = AuthContext.requireSelfStudentOrAdmin(studentId);
        return Result.success(examService.listRecordsByStudent(selfId));
    }
}

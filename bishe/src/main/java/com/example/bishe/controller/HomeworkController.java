package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.AuthUser;
import com.example.bishe.common.Result;
import com.example.bishe.dto.GradeAnswerRequest;
import com.example.bishe.dto.HomeworkDetailResponse;
import com.example.bishe.dto.HomeworkPublishRequest;
import com.example.bishe.dto.HomeworkReviewResponse;
import com.example.bishe.dto.HomeworkSubmitRequest;
import com.example.bishe.dto.PendingGradeItemVO;
import com.example.bishe.dto.ScoreRankItemVO;
import com.example.bishe.entity.Homework;
import com.example.bishe.entity.UserHomeworkRecord;
import com.example.bishe.service.HomeworkService;
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
 * 作业相关接口(教师端: 发布作业/批改解答题; 学生端: 完成并提交作业)。
 */
@Tag(name = "作业", description = "教师发布作业与批改、学生完成作业")
@RestController
@RequestMapping("/api/homework")
public class HomeworkController {

    private final HomeworkService homeworkService;

    public HomeworkController(HomeworkService homeworkService) {
        this.homeworkService = homeworkService;
    }

    // ==================== 教师端 ====================

    /** 教师发布作业(组卷: 选定题目ID列表与各题分值, 并指定截止时间) */
    @Operation(summary = "教师发布作业")
    @PostMapping("/publish")
    public Result<Homework> publish(@RequestBody HomeworkPublishRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        request.setTeacherId(teacherId);
        return Result.success("作业发布成功", homeworkService.publishHomework(request));
    }

    /** 查询当前用户有权访问的某课程下全部作业 */
    @Operation(summary = "查询某课程下的全部作业")
    @GetMapping("/course/{courseId}")
    public Result<List<Homework>> byCourse(@PathVariable Long courseId) {
        AuthUser user = AuthContext.requireLogin();
        return Result.success(homeworkService.listByCourse(courseId, user));
    }

    /** 查询某教师发布的全部作业 */
    @Operation(summary = "查询某教师发布的全部作业")
    @GetMapping("/teacher/{teacherId}")
    public Result<List<Homework>> byTeacher(@PathVariable Long teacherId) {
        Long selfId = AuthContext.requireSelfTeacherOrAdmin(teacherId);
        return Result.success(homeworkService.listByTeacher(selfId));
    }

    /** 查询某次作业待人工批改的解答题列表 */
    @Operation(summary = "查询某次作业待人工批改的解答题列表")
    @GetMapping("/{homeworkId}/pending-grade")
    public Result<List<PendingGradeItemVO>> pendingGrade(@PathVariable Long homeworkId) {
        Long teacherId = AuthContext.requireTeacherId();
        return Result.success(homeworkService.listPendingGrade(teacherId, homeworkId));
    }

    /** 教师人工批改解答题 */
    @Operation(summary = "教师人工批改解答题")
    @PostMapping("/grade")
    public Result<Void> grade(@RequestBody GradeAnswerRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        homeworkService.gradeSolveAnswer(teacherId, request);
        return Result.success("批改成功", null);
    }

    /** 教师公布作业成绩与答案 */
    @Operation(summary = "教师公布作业成绩与答案")
    @PostMapping("/{homeworkId}/publish-result")
    public Result<Void> publishResult(@PathVariable Long homeworkId) {
        Long teacherId = AuthContext.requireTeacherId();
        homeworkService.publishResult(teacherId, homeworkId);
        return Result.success("作业成绩已公布", null);
    }

    /** 教师查看作业成绩排名 */
    @Operation(summary = "教师查看作业成绩排名")
    @GetMapping("/{homeworkId}/scores")
    public Result<List<ScoreRankItemVO>> scores(@PathVariable Long homeworkId) {
        Long teacherId = AuthContext.requireTeacherId();
        return Result.success(homeworkService.listScoreRanking(teacherId, homeworkId));
    }

    /** 教师查看指定学生作业作答 */
    @Operation(summary = "教师查看指定学生作业作答")
    @GetMapping("/{homeworkId}/student/{studentId}/review")
    public Result<HomeworkReviewResponse> teacherReview(@PathVariable Long homeworkId,
                                                         @PathVariable Long studentId) {
        Long teacherId = AuthContext.requireTeacherId();
        return Result.success(homeworkService.reviewHomeworkForTeacher(teacherId, homeworkId, studentId));
    }

    // ==================== 学生端 ====================

    /** 查询作业详情。答案是否可见完全由服务端根据当前角色决定。 */
    @Operation(summary = "查询作业详情")
    @GetMapping("/{homeworkId}")
    public Result<HomeworkDetailResponse> detail(@PathVariable Long homeworkId) {
        AuthUser user = AuthContext.requireLogin();
        return Result.success(homeworkService.getHomeworkDetail(homeworkId, user));
    }

    /** 学生提交作业答案, 选择题/判断题自动批改, 解答题进入待批改 */
    @Operation(summary = "学生提交作业答案")
    @PostMapping("/submit")
    public Result<HomeworkReviewResponse> submit(@RequestBody HomeworkSubmitRequest request) {
        Long studentId = AuthContext.requireStudentId();
        request.setStudentId(studentId);
        return Result.success("作业提交成功", homeworkService.submitHomework(request));
    }

    /** 学生查看自己某次作业的作答复盘 */
    @Operation(summary = "学生查看某次作业的作答复盘")
    @GetMapping("/{homeworkId}/review")
    public Result<HomeworkReviewResponse> review(@PathVariable Long homeworkId,
                                                  @RequestParam(required = false) Long studentId) {
        Long selfId = studentId == null
                ? AuthContext.requireStudentId()
                : AuthContext.requireSelfStudentOrAdmin(studentId);
        return Result.success(homeworkService.reviewHomework(homeworkId, selfId));
    }

    /** 查询某学生的全部作业记录(总分列表) */
    @Operation(summary = "查询某学生的全部作业记录")
    @GetMapping("/student/{studentId}/records")
    public Result<List<UserHomeworkRecord>> studentRecords(@PathVariable Long studentId) {
        Long selfId = AuthContext.requireSelfStudentOrAdmin(studentId);
        return Result.success(homeworkService.listRecordsByStudent(selfId));
    }
}

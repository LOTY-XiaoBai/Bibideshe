package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.CourseCreateRequest;
import com.example.bishe.dto.CourseEnrollRequest;
import com.example.bishe.dto.CourseMemberRequest;
import com.example.bishe.dto.CourseMemberVO;
import com.example.bishe.dto.CourseTeacherRequest;
import com.example.bishe.dto.ClassRankVO;
import com.example.bishe.dto.StudentScoreVO;
import com.example.bishe.entity.Course;
import com.example.bishe.service.CourseService;
import com.example.bishe.service.ScoreCompareService;
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
 * 课程管理接口(教师端 + 学生端)。
 * 教师身份一律取自登录 Token, 忽略请求体中的 teacherId 伪装。
 */
@Tag(name = "课程", description = "选课、班级、成绩与班级排名")
@RestController
@RequestMapping("/api/course")
public class CourseController {

    private final CourseService courseService;
    private final ScoreCompareService scoreCompareService;

    public CourseController(CourseService courseService, ScoreCompareService scoreCompareService) {
        this.courseService = courseService;
        this.scoreCompareService = scoreCompareService;
    }

    // ==================== 教师端 ====================

    /** 教师创建课程 */
    @Operation(summary = "教师创建课程")
    @PostMapping("/create")
    public Result<Course> create(@RequestBody CourseCreateRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        request.setTeacherId(teacherId);
        return Result.success("课程创建成功", courseService.createCourse(request));
    }

    /** 教师结束课程 */
    @PostMapping("/finish")
    public Result<Void> finish(@RequestBody CourseTeacherRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        courseService.finishCourse(teacherId, request.getCourseId());
        return Result.success("课程已结束", null);
    }

    /** 教师取消结课(恢复课程为进行中) */
    @PostMapping("/unfinish")
    public Result<Void> unfinish(@RequestBody CourseTeacherRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        courseService.unfinishCourse(teacherId, request.getCourseId());
        return Result.success("课程已恢复为进行中", null);
    }

    /** 教师删除课程 */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody CourseTeacherRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        courseService.deleteCourseByTeacher(teacherId, request.getCourseId());
        return Result.success("课程已删除", null);
    }

    /** 教师移除班级中的学生 */
    @PostMapping("/remove-student")
    public Result<Void> removeStudent(@RequestBody CourseMemberRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        courseService.removeStudent(teacherId, request.getCourseId(), request.getStudentId());
        return Result.success("已将该学生移出课程", null);
    }

    /** 查询当前教师创建的全部课程(path 仅允许查询自己) */
    @GetMapping("/teacher/{teacherId}")
    public Result<List<Course>> teacherCourses(@PathVariable Long teacherId) {
        Long selfId = AuthContext.requireSelfTeacherOrAdmin(teacherId);
        return Result.success(courseService.listTeacherCourses(selfId));
    }

    /** 查询当前登录教师的课程列表 */
    @GetMapping("/teacher/mine")
    public Result<List<Course>> myTeacherCourses() {
        return Result.success(courseService.listTeacherCourses(AuthContext.requireTeacherId()));
    }

    // ==================== 学生端 ====================

    /** 通过课程号搜索课程 */
    @GetMapping("/search")
    public Result<Course> search(@RequestParam Long courseId) {
        AuthContext.requireLogin();
        return Result.success(courseService.searchCourse(courseId));
    }

    /** 学生加入班级(选课) */
    @PostMapping("/join")
    public Result<Void> join(@RequestBody CourseEnrollRequest request) {
        Long studentId = AuthContext.requireStudentId();
        return Result.success(courseService.joinCourse(studentId, request.getCourseId()), null);
    }

    @GetMapping("/applications/teacher")
    public Result<?> teacherApplications() {
        return Result.success(courseService.applications(AuthContext.requireTeacherId(), true));
    }

    @GetMapping("/applications/student")
    public Result<?> studentApplications() {
        return Result.success(courseService.applications(AuthContext.requireStudentId(), false));
    }

    @PostMapping("/applications/review")
    public Result<?> reviewApplication(@RequestBody ApplicationReview request) {
        if (request.approve() == null) throw new com.example.bishe.common.BusinessException("请选择审核结果");
        courseService.reviewApplication(AuthContext.requireTeacherId(), request.courseId(), request.studentId(), request.approve());
        return Result.success(null);
    }

    public record ApplicationReview(Long courseId, Long studentId, Boolean approve) {}

    /** 学生退出班级(退课) */
    @PostMapping("/leave")
    public Result<Void> leave(@RequestBody CourseEnrollRequest request) {
        Long studentId = AuthContext.requireStudentId();
        courseService.leaveCourse(studentId, request.getCourseId());
        return Result.success("退课成功", null);
    }

    /** 查询学生已选的全部课程 */
    @GetMapping("/student/{studentId}")
    public Result<List<Course>> studentCourses(@PathVariable Long studentId) {
        Long selfId = AuthContext.requireSelfStudentOrAdmin(studentId);
        return Result.success(courseService.listStudentCourses(selfId));
    }

    /** 查询当前登录学生的课程列表 */
    @GetMapping("/student/mine")
    public Result<List<Course>> myStudentCourses() {
        return Result.success(courseService.listStudentCourses(AuthContext.requireStudentId()));
    }

    // ==================== 通用查询 ====================

    /** 查询课程的班级成员 */
    @GetMapping("/{courseId}/members")
    public Result<List<CourseMemberVO>> members(@PathVariable Long courseId) {
        AuthContext.requireLogin();
        return Result.success(courseService.listMembers(courseId));
    }

    // ==================== 学生成绩 ====================

    /** 查询学生的总分(所有已结课课程 student_score 的平均分) */
    @GetMapping("/student/{studentId}/total-score")
    public Result<StudentScoreVO> studentTotalScore(@PathVariable Long studentId) {
        Long selfId = AuthContext.requireSelfStudentOrAdmin(studentId);
        return Result.success(courseService.getStudentTotalScore(selfId));
    }

    /** 查询当前学生全部课程各自得分 */
    @GetMapping("/student/mine/scores")
    public Result<List<com.example.bishe.dto.CourseScoreItemVO>> myCourseScores() {
        Long studentId = AuthContext.requireStudentId();
        return Result.success(courseService.listStudentCourseScores(studentId));
    }

    /** 查询当前学生某门课程得分 */
    @Operation(summary = "当前学生某门课程得分")
    @GetMapping("/student/mine/score")
    public Result<com.example.bishe.dto.CourseScoreItemVO> myCourseScore(@RequestParam Long courseId) {
        Long studentId = AuthContext.requireStudentId();
        return Result.success(courseService.getStudentCourseScore(studentId, courseId));
    }

    /**
     * 班级成绩排名: 返回名次与同学昵称, 不展示其他学生具体分数。
     */
    @Operation(summary = "班级成绩排名(不显示他人分数)")
    @GetMapping("/student/mine/rank")
    public Result<ClassRankVO> myClassRank(@RequestParam Long courseId) {
        Long studentId = AuthContext.requireStudentId();
        return Result.success(scoreCompareService.classRanking(studentId, courseId));
    }
}

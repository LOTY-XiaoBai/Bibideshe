package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.CheckInDetailVO;
import com.example.bishe.dto.CheckInBounds;
import com.example.bishe.dto.CheckInPublishRequest;
import com.example.bishe.dto.CheckInRequest;
import com.example.bishe.dto.CheckInStatusUpdateRequest;
import com.example.bishe.dto.CheckInStudentRecordVO;
import com.example.bishe.entity.CheckInRecord;
import com.example.bishe.entity.CheckInUser;
import com.example.bishe.service.CheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 签到管理接口(教师端 + 学生端)。教师身份取自 Token。
 */
@Tag(name = "签到", description = "教师发布签到与学生签到管理")
@RestController
@RequestMapping("/api/check-in")
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    // ==================== 教师端 ====================

    /** 教师发布签到 */
    @Operation(summary = "教师发布签到")
    @PostMapping("/publish")
    public Result<CheckInRecord> publish(@RequestBody CheckInPublishRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        request.setTeacherId(teacherId);
        return Result.success("签到发布成功", checkInService.publishCheckIn(request));
    }

    /** 教师手动结束签到 */
    @Operation(summary = "教师手动结束签到")
    @PostMapping("/end")
    public Result<Void> end(@RequestParam Long checkInId) {
        Long teacherId = AuthContext.requireTeacherId();
        checkInService.endCheckIn(teacherId, checkInId);
        return Result.success("签到已结束", null);
    }

    /** 教师查看某次签到的全体学生签到详情 */
    @Operation(summary = "教师查看某次签到的全体学生签到详情")
    @GetMapping("/detail/{checkInId}")
    public Result<List<CheckInDetailVO>> detail(@PathVariable Long checkInId) {
        Long teacherId = AuthContext.requireTeacherId();
        return Result.success(checkInService.getCheckInDetail(teacherId, checkInId));
    }

    @GetMapping("/display/{checkInId}")
    public Result<Map<String, Object>> display(@PathVariable Long checkInId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.success(checkInService.display(AuthContext.requireTeacherId(), checkInId));
    }

    @GetMapping("/location-default/{courseId}")
    public Result<CheckInBounds> locationDefault(@PathVariable Long courseId) {
        return Result.success(checkInService.locationDefault(AuthContext.requireTeacherId(), courseId));
    }

    /** 教师修改学生签到状态(签到结束后可操作) */
    @Operation(summary = "教师修改学生签到状态")
    @PostMapping("/update-status")
    public Result<Void> updateStatus(@RequestBody CheckInStatusUpdateRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        request.setTeacherId(teacherId);
        checkInService.updateStudentStatus(request);
        return Result.success("状态修改成功", null);
    }

    /** 教师查看课程下所有签到记录(分页) */
    @Operation(summary = "教师查看课程下所有签到记录")
    @GetMapping("/course/{courseId}")
    public Result<Map<String, Object>> courseCheckIns(@PathVariable Long courseId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        Long teacherId = AuthContext.requireTeacherId();
        return Result.success(checkInService.listCourseCheckIns(teacherId, courseId, page, size));
    }

    /** 教师查看某次签到中缺勤(漏签)的学生 */
    @Operation(summary = "教师查看某次签到中缺勤的学生")
    @GetMapping("/absent/{checkInId}")
    public Result<List<CheckInDetailVO>> absentStudents(@PathVariable Long checkInId) {
        Long teacherId = AuthContext.requireTeacherId();
        return Result.success(checkInService.getAbsentStudents(teacherId, checkInId));
    }

    // ==================== 学生端 ====================

    @PostMapping("/location-preview/{checkInId}")
    public Result<CheckInService.LocationPreview> previewLocation(@PathVariable Long checkInId,
            @RequestBody CheckInRequest.Position position, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.success(checkInService.previewLocation(checkInId, AuthContext.requireStudentId(), position));
    }

    /** 学生签到 */
    @Operation(summary = "学生签到")
    @PostMapping("/sign")
    public Result<Void> sign(@RequestBody CheckInRequest request) {
        Long studentId = AuthContext.requireStudentId();
        checkInService.studentCheckIn(request, studentId);
        return Result.success("签到成功", null);
    }

    /** 学生查看自己的签到记录(分页) */
    @Operation(summary = "学生查看自己的签到记录")
    @GetMapping("/student/{studentId}/records")
    public Result<Map<String, Object>> studentRecords(@PathVariable Long studentId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        Long selfId = AuthContext.requireSelfStudentOrAdmin(studentId);
        return Result.success(checkInService.getStudentRecords(selfId, page, size));
    }

    /** 学生查看某课程下的漏签记录 */
    @Operation(summary = "学生查看某课程下的漏签记录")
    @GetMapping("/student/{studentId}/missed/{courseId}")
    public Result<List<CheckInStudentRecordVO>> missedByCourse(@PathVariable Long studentId,
                                                                @PathVariable Long courseId) {
        Long selfId = AuthContext.requireSelfStudentOrAdmin(studentId);
        return Result.success(checkInService.getMissedRecordsByCourse(selfId, courseId));
    }

    /** 学生查看全部漏签记录(分页) */
    @Operation(summary = "学生查看全部漏签记录")
    @GetMapping("/student/{studentId}/missed")
    public Result<Map<String, Object>> allMissed(@PathVariable Long studentId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        Long selfId = AuthContext.requireSelfStudentOrAdmin(studentId);
        return Result.success(checkInService.getAllMissedRecords(selfId, page, size));
    }

    /** 学生查看某次签到的个人状态 */
    @Operation(summary = "学生查看某次签到的个人状态")
    @GetMapping("/status/{checkInId}")
    public Result<CheckInUser> myStatus(@PathVariable Long checkInId) {
        Long studentId = AuthContext.requireStudentId();
        return Result.success(checkInService.getMyCheckInStatus(checkInId, studentId));
    }
}

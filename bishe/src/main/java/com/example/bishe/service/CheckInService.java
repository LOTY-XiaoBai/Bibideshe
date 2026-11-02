package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.dto.CheckInDetailVO;
import com.example.bishe.dto.CheckInPublishRequest;
import com.example.bishe.dto.CheckInRecordVO;
import com.example.bishe.dto.CheckInStatusUpdateRequest;
import com.example.bishe.dto.CheckInStudentRecordVO;
import com.example.bishe.entity.CheckInRecord;
import com.example.bishe.entity.CheckInUser;
import com.example.bishe.entity.Course;
import com.example.bishe.entity.CoursePersonnel;
import com.example.bishe.mapper.CheckInRecordMapper;
import com.example.bishe.mapper.CheckInUserMapper;
import com.example.bishe.mapper.CourseMapper;
import com.example.bishe.mapper.CoursePersonnelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 签到管理服务。
 * 包含教师端(发布/结束签到、查看签到详情、修改学生签到状态)与学生端(签到、查看记录、查看漏签)。
 */
@Service
public class CheckInService {

    /** 签到状态常量 */
    private static final int CHECK_IN_ACTIVE = 0;   // 进行中
    private static final int CHECK_IN_ENDED = 1;    // 已结束

    /** 学生签到状态常量 */
    private static final int STUDENT_ABSENT = 0;    // 缺勤
    private static final int STUDENT_PRESENT = 1;   // 已签到
    private static final int STUDENT_LATE = 2;      // 迟到

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final CheckInRecordMapper checkInRecordMapper;
    private final CheckInUserMapper checkInUserMapper;
    private final CourseMapper courseMapper;
    private final CoursePersonnelMapper coursePersonnelMapper;
    private final CourseService courseService;

    public CheckInService(CheckInRecordMapper checkInRecordMapper,
                          CheckInUserMapper checkInUserMapper,
                          CourseMapper courseMapper,
                          CoursePersonnelMapper coursePersonnelMapper,
                          CourseService courseService) {
        this.checkInRecordMapper = checkInRecordMapper;
        this.checkInUserMapper = checkInUserMapper;
        this.courseMapper = courseMapper;
        this.coursePersonnelMapper = coursePersonnelMapper;
        this.courseService = courseService;
    }

    // ==================== 教师端 ====================

    /**
     * 教师发布签到。
     * 为课程下所有学生创建签到明细(初始状态为缺勤)。
     */
    @Transactional
    public CheckInRecord publishCheckIn(CheckInPublishRequest request) {
        if (request.getTeacherId() == null || request.getCourseId() == null) {
            throw new BusinessException("教师ID和课程ID不能为空");
        }
        if (request.getDeadline() == null) {
            throw new BusinessException("签到截止时间不能为空");
        }
        if (request.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException("截止时间不能早于当前时间");
        }
        if (request.getScoreValue() == null || request.getScoreValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("签到分值必须大于0");
        }

        // 校验课程归属
        Course course = requireOwnedCourse(request.getTeacherId(), request.getCourseId());
        if (course.getIsFinished() != null && course.getIsFinished() == 1) {
            throw new BusinessException("课程已结束, 无法发布签到");
        }

        // 获取课程下所有学生
        List<CoursePersonnel> students = coursePersonnelMapper.findByCourseId(request.getCourseId());
        if (students.isEmpty()) {
            throw new BusinessException("该课程暂无学生, 无法发起签到");
        }

        // 创建签到记录
        CheckInRecord record = new CheckInRecord();
        record.setCourseId(request.getCourseId());
        record.setPublisherId(request.getTeacherId());
        record.setPublishTime(LocalDateTime.now());
        record.setDeadline(request.getDeadline());
        record.setAttendeeCount(0);
        record.setStatus(CHECK_IN_ACTIVE);
        record.setScoreValue(request.getScoreValue());
        checkInRecordMapper.insert(record);

        // 为每位学生创建签到明细(初始缺勤)
        List<CheckInUser> userList = new ArrayList<>();
        for (CoursePersonnel student : students) {
            CheckInUser user = new CheckInUser();
            user.setCheckInId(record.getCheckInId());
            user.setStudentId(student.getStudentId());
            user.setStatus(STUDENT_ABSENT);
            userList.add(user);
        }
        checkInUserMapper.batchInsert(userList);

        // 发布后刷新课程成绩(缺勤暂不计分, 但总分母已变化)
        refreshCourseScores(request.getCourseId(), students);

        return record;
    }

    /**
     * 教师手动结束签到。
     */
    @Transactional
    public void endCheckIn(Long teacherId, Long checkInId) {
        CheckInRecord record = checkInRecordMapper.findById(checkInId);
        if (record == null) {
            throw new BusinessException("签到记录不存在");
        }
        requireOwnedCourse(teacherId, record.getCourseId());

        // 检查是否已过期(自动结束)
        autoEndIfExpired(record);
        CheckInRecord latest = checkInRecordMapper.findById(checkInId);
        if (latest.getStatus() != null && latest.getStatus() == CHECK_IN_ENDED) {
            throw new BusinessException("签到已结束, 请勿重复操作");
        }

        checkInRecordMapper.endCheckIn(checkInId, LocalDateTime.now());
        // 结束后回写课程成绩
        refreshCourseScores(record.getCourseId(),
                coursePersonnelMapper.findByCourseId(record.getCourseId()));
    }

    /**
     * 教师查看某次签到的全体学生签到详情。
     */
    public List<CheckInDetailVO> getCheckInDetail(Long teacherId, Long checkInId) {
        CheckInRecord record = checkInRecordMapper.findById(checkInId);
        if (record == null) {
            throw new BusinessException("签到记录不存在");
        }
        requireOwnedCourse(teacherId, record.getCourseId());

        // 检查并自动处理过期签到
        autoEndIfExpired(record);

        return checkInUserMapper.findDetailsByCheckInId(checkInId);
    }

    /**
     * 教师修改学生签到状态(签到结束后可操作)。
     * 支持将"已签到"改为"未签到", 或将"未签到"改为"已签到"。
     */
    @Transactional
    public void updateStudentStatus(CheckInStatusUpdateRequest request) {
        if (request.getCheckInId() == null || request.getStudentId() == null) {
            throw new BusinessException("签到ID和学生ID不能为空");
        }
        if (request.getStatus() == null || (request.getStatus() != STUDENT_ABSENT
                && request.getStatus() != STUDENT_PRESENT)) {
            throw new BusinessException("状态值无效, 仅支持0(缺勤)或1(已签到)");
        }

        CheckInRecord record = checkInRecordMapper.findById(request.getCheckInId());
        if (record == null) {
            throw new BusinessException("签到记录不存在");
        }
        requireOwnedCourse(request.getTeacherId(), record.getCourseId());

        // 签到已结束后才允许修改
        autoEndIfExpired(record);
        CheckInRecord updated = checkInRecordMapper.findById(request.getCheckInId());
        if (updated.getStatus() != CHECK_IN_ENDED) {
            throw new BusinessException("签到尚未结束, 结束后才可修改学生状态");
        }

        // 检查学生是否在该签到中
        CheckInUser user = checkInUserMapper.findByCheckInIdAndStudentId(
                request.getCheckInId(), request.getStudentId());
        if (user == null) {
            throw new BusinessException("该学生不在本次签到记录中");
        }

        LocalDateTime now = LocalDateTime.now();
        checkInUserMapper.updateStatus(request.getCheckInId(), request.getStudentId(),
                request.getStatus(), now);

        // 同步更新签到人数
        syncAttendeeCount(request.getCheckInId());
        // 状态变更后回写该学生课程成绩
        courseService.recalculateAndUpdateStudentScore(record.getCourseId(), request.getStudentId());
        courseService.updateComprehensiveScoreForStudent(request.getStudentId());
    }

    /**
     * 查看课程下所有签到记录(分页)。
     */
    public Map<String, Object> listCourseCheckIns(Long teacherId, Long courseId, int page, int size) {
        requireOwnedCourse(teacherId, courseId);
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (page <= 0) page = 1;
        int offset = (page - 1) * size;

        int total = checkInRecordMapper.countByCourseId(courseId);
        List<CheckInRecord> records = checkInRecordMapper.findByCourseId(courseId, offset, size);

        // 自动处理过期签到
        for (CheckInRecord record : records) {
            autoEndIfExpired(record);
        }

        // 补充课程名称
        Course course = courseMapper.findById(courseId);
        List<CheckInRecordVO> vos = new ArrayList<>();
        for (CheckInRecord r : records) {
            CheckInRecordVO vo = new CheckInRecordVO();
            vo.setCheckInId(r.getCheckInId());
            vo.setCourseId(r.getCourseId());
            vo.setCourseName(course != null ? course.getCourseName() : "");
            vo.setPublishTime(r.getPublishTime());
            vo.setDeadline(r.getDeadline());
            vo.setEndTime(r.getEndTime());
            vo.setAttendeeCount(r.getAttendeeCount());
            vo.setStatus(r.getStatus());
            vo.setScoreValue(r.getScoreValue());
            vos.add(vo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("records", vos);
        return result;
    }

    /**
     * 查看某次签到中缺勤(漏签)的学生列表。
     */
    public List<CheckInDetailVO> getAbsentStudents(Long teacherId, Long checkInId) {
        CheckInRecord record = checkInRecordMapper.findById(checkInId);
        if (record == null) {
            throw new BusinessException("签到记录不存在");
        }
        requireOwnedCourse(teacherId, record.getCourseId());
        autoEndIfExpired(record);

        List<CheckInDetailVO> all = checkInUserMapper.findDetailsByCheckInId(checkInId);
        List<CheckInDetailVO> absent = new ArrayList<>();
        for (CheckInDetailVO detail : all) {
            if (detail.getStudentStatus() != null && detail.getStudentStatus() == STUDENT_ABSENT) {
                absent.add(detail);
            }
        }
        return absent;
    }

    // ==================== 学生端 ====================

    /**
     * 学生签到。
     * 截止时间前签到记"已签到", 截止后记"迟到"; 签到结束后不可签到。
     */
    @Transactional
    public void studentCheckIn(Long checkInId, Long studentId) {
        if (checkInId == null || studentId == null) {
            throw new BusinessException("签到ID和学生ID不能为空");
        }

        CheckInRecord record = checkInRecordMapper.findById(checkInId);
        if (record == null) {
            throw new BusinessException("签到记录不存在");
        }

        // 检查学生是否在该课程中
        CoursePersonnel enrollment = coursePersonnelMapper.find(record.getCourseId(), studentId);
        if (enrollment == null) {
            throw new BusinessException("您未加入该课程, 无法签到");
        }

        // 检查签到是否已过期, 若过期则自动结束
        autoEndIfExpired(record);

        // 检查学生签到记录
        CheckInUser user = checkInUserMapper.findByCheckInIdAndStudentId(checkInId, studentId);
        if (user == null) {
            throw new BusinessException("签到记录异常, 请联系教师");
        }

        if (user.getStatus() != null && user.getStatus() != STUDENT_ABSENT) {
            throw new BusinessException("您已签到, 请勿重复操作");
        }

        LocalDateTime now = LocalDateTime.now();
        int signStatus;

        // 签到已结束后不再允许签到(已由autoEndIfExpired处理过期)
        CheckInRecord latest = checkInRecordMapper.findById(checkInId);
        if (latest.getStatus() != null && latest.getStatus() == CHECK_IN_ENDED) {
            throw new BusinessException("签到已结束, 无法签到");
        }

        // 判断签到状态: 截止前签到=正常, 截止后签到=迟到
        if (now.isAfter(record.getDeadline())) {
            signStatus = STUDENT_LATE;
        } else {
            signStatus = STUDENT_PRESENT;
        }

        checkInUserMapper.checkIn(checkInId, studentId, signStatus, now);
        syncAttendeeCount(checkInId);
        // 签到后回写课程成绩
        courseService.recalculateAndUpdateStudentScore(record.getCourseId(), studentId);
        courseService.updateComprehensiveScoreForStudent(studentId);
    }

    /**
     * 查看学生签到记录(分页)。
     */
    public Map<String, Object> getStudentRecords(Long studentId, int page, int size) {
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (page <= 0) page = 1;
        int offset = (page - 1) * size;

        int total = checkInUserMapper.countByStudentId(studentId);
        List<CheckInStudentRecordVO> records = checkInUserMapper.findRecordsByStudentId(
                studentId, offset, size);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("records", records);
        return result;
    }

    /**
     * 查看学生某课程下的漏签记录。
     */
    public List<CheckInStudentRecordVO> getMissedRecordsByCourse(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) {
            throw new BusinessException("学生ID和课程ID不能为空");
        }
        return checkInUserMapper.findMissedByStudentIdAndCourseId(studentId, courseId);
    }

    /**
     * 查看学生全部漏签记录(分页)。
     */
    public Map<String, Object> getAllMissedRecords(Long studentId, int page, int size) {
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (page <= 0) page = 1;
        int offset = (page - 1) * size;

        int total = checkInUserMapper.countMissedByStudentId(studentId);
        List<CheckInStudentRecordVO> records = checkInUserMapper.findAllMissedByStudentId(
                studentId, offset, size);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("records", records);
        return result;
    }

    /**
     * 学生查看某次签到的状态。
     */
    public CheckInUser getMyCheckInStatus(Long checkInId, Long studentId) {
        if (checkInId == null || studentId == null) {
            throw new BusinessException("签到ID和学生ID不能为空");
        }
        CheckInUser user = checkInUserMapper.findByCheckInIdAndStudentId(checkInId, studentId);
        if (user == null) {
            throw new BusinessException("签到记录不存在");
        }
        return user;
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 校验课程存在且归属于指定教师, 返回课程。
     */
    private Course requireOwnedCourse(Long teacherId, Long courseId) {
        if (teacherId == null || courseId == null) {
            throw new BusinessException("教师ID和课程ID不能为空");
        }
        Course course = courseMapper.findById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException("无权操作该课程(非本人创建)");
        }
        return course;
    }

    /**
     * 检查签到是否已过期, 若过期则自动结束。
     */
    private void autoEndIfExpired(CheckInRecord record) {
        if (record.getStatus() != null && record.getStatus() == CHECK_IN_ENDED) {
            return;
        }
        if (record.getDeadline() != null && LocalDateTime.now().isAfter(record.getDeadline())) {
            checkInRecordMapper.endCheckIn(record.getCheckInId(), LocalDateTime.now());
            record.setStatus(CHECK_IN_ENDED);
        }
    }

    /**
     * 同步更新签到人数(统计已签到+迟到的人数)。
     */
    private void syncAttendeeCount(Long checkInId) {
        // 简单实现: 通过查询详情列表来统计非缺勤人数
        List<CheckInDetailVO> details = checkInUserMapper.findDetailsByCheckInId(checkInId);
        int count = 0;
        for (CheckInDetailVO detail : details) {
            if (detail.getStudentStatus() != null && detail.getStudentStatus() != STUDENT_ABSENT) {
                count++;
            }
        }
        checkInRecordMapper.updateAttendeeCount(checkInId, count);
    }

    /**
     * 对课程下学生重算课程总分与综合评分。
     */
    private void refreshCourseScores(Long courseId, List<CoursePersonnel> students) {
        for (CoursePersonnel student : students) {
            courseService.recalculateAndUpdateStudentScore(courseId, student.getStudentId());
            courseService.updateComprehensiveScoreForStudent(student.getStudentId());
        }
    }
}

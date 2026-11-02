package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.dto.CourseCreateRequest;
import com.example.bishe.dto.CourseMemberVO;
import com.example.bishe.dto.StudentScoreVO;
import com.example.bishe.entity.Course;
import com.example.bishe.entity.CoursePersonnel;
import com.example.bishe.mapper.CascadeMapper;
import com.example.bishe.mapper.CourseMapper;
import com.example.bishe.mapper.CoursePersonnelMapper;
import com.example.bishe.mapper.StudentMapper;
import com.example.bishe.mapper.TeacherMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 课程管理服务。包含教师端(创建/结束/删除课程、移除学生)与学生端(搜索/选课/退课)功能,
 * 以及供管理员复用的课程级联删除逻辑。
 */
@Service
public class CourseService {

    /** 单个教师可创建的课程数量上限 */
    private static final int MAX_COURSE_PER_TEACHER = 3;

    private static final int NOT_FINISHED = 0;
    private static final int FINISHED = 1;

    private final CourseMapper courseMapper;
    private final CoursePersonnelMapper coursePersonnelMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final CascadeMapper cascadeMapper;

    public CourseService(CourseMapper courseMapper,
                         CoursePersonnelMapper coursePersonnelMapper,
                         TeacherMapper teacherMapper,
                         StudentMapper studentMapper,
                         CascadeMapper cascadeMapper) {
        this.courseMapper = courseMapper;
        this.coursePersonnelMapper = coursePersonnelMapper;
        this.teacherMapper = teacherMapper;
        this.studentMapper = studentMapper;
        this.cascadeMapper = cascadeMapper;
    }

    // ==================== 教师端 ====================

    /**
     * 教师创建课程。校验课程数量上限为3, 建课后教师表 course_count +1。
     */
    @Transactional
    public Course createCourse(CourseCreateRequest request) {
        if (request.getTeacherId() == null) {
            throw new BusinessException("教师ID不能为空");
        }
        if (!StringUtils.hasText(request.getCourseName())) {
            throw new BusinessException("课程名称不能为空");
        }
        if (teacherMapper.findById(request.getTeacherId()) == null) {
            throw new BusinessException("教师不存在");
        }
        int current = courseMapper.countActiveByTeacherId(request.getTeacherId());
        if (current >= MAX_COURSE_PER_TEACHER) {
            throw new BusinessException("进行中的课程数量已达上限(" + MAX_COURSE_PER_TEACHER + "门), 无法继续创建");
        }

        Course course = new Course();
        course.setTeacherId(request.getTeacherId());
        course.setCourseName(request.getCourseName());
        course.setIsFinished(NOT_FINISHED);
        courseMapper.insert(course);

        teacherMapper.incrementCourseCount(request.getTeacherId());
        return course;
    }

    /**
     * 教师结束课程。结课后触发该课程下所有学生的综合评分重算。
     */
    @Transactional
    public void finishCourse(Long teacherId, Long courseId) {
        Course course = requireOwnedCourse(teacherId, courseId);
        if (course.getIsFinished() != null && course.getIsFinished() == FINISHED) {
            throw new BusinessException("课程已经结束, 请勿重复操作");
        }
        courseMapper.updateFinished(courseId, FINISHED);
        // 课程结课后重算所有学生的综合评分
        updateComprehensiveScoreForAllStudentsInCourse(courseId);
    }

    /**
     * 教师取消结课(将课程恢复为进行中)。恢复后触发该课程下所有学生的综合评分重算,
     * 因为该课程将不再参与综合评分计算。
     */
    @Transactional
    public void unfinishCourse(Long teacherId, Long courseId) {
        Course course = requireOwnedCourse(teacherId, courseId);
        if (course.getIsFinished() == null || course.getIsFinished() == NOT_FINISHED) {
            throw new BusinessException("课程尚未结束, 请勿重复操作");
        }
        courseMapper.updateFinished(courseId, NOT_FINISHED);
        // 课程恢复为进行中后重算所有学生的综合评分(该课程不再计入)
        updateComprehensiveScoreForAllStudentsInCourse(courseId);
    }

    /**
     * 教师删除自己的课程(级联删除课程下所有记录), 并使教师表 course_count -1。
     */
    @Transactional
    public void deleteCourseByTeacher(Long teacherId, Long courseId) {
        requireOwnedCourse(teacherId, courseId);
        deleteCourseCascade(courseId);
        teacherMapper.decrementCourseCount(teacherId);
    }

    /**
     * 教师移除班级中的某个学生。
     */
    @Transactional
    public void removeStudent(Long teacherId, Long courseId, Long studentId) {
        requireOwnedCourse(teacherId, courseId);
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        if (coursePersonnelMapper.find(courseId, studentId) == null) {
            throw new BusinessException("该学生不在此课程中");
        }
        coursePersonnelMapper.delete(courseId, studentId);
    }

    // ==================== 学生端 ====================

    /**
     * 通过课程号搜索课程。
     */
    public Course searchCourse(Long courseId) {
        if (courseId == null) {
            throw new BusinessException("课程号不能为空");
        }
        Course course = courseMapper.findById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return course;
    }

    /**
     * 学生加入班级(选课)。防止重复选课。
     */
    @Transactional
    public void joinCourse(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) {
            throw new BusinessException("学生ID与课程号不能为空");
        }
        if (studentMapper.findById(studentId) == null) {
            throw new BusinessException("学生不存在");
        }
        Course course = courseMapper.findById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (course.getIsFinished() != null && course.getIsFinished() == FINISHED) {
            throw new BusinessException("该课程已结束, 无法加入");
        }
        if (coursePersonnelMapper.find(courseId, studentId) != null) {
            throw new BusinessException("您已选修该课程, 请勿重复选课");
        }

        CoursePersonnel personnel = new CoursePersonnel();
        personnel.setCourseId(courseId);
        personnel.setStudentId(studentId);
        personnel.setStudentScore(BigDecimal.ZERO);
        coursePersonnelMapper.insert(personnel);
    }

    /**
     * 学生退出班级(退课)。
     */
    @Transactional
    public void leaveCourse(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) {
            throw new BusinessException("学生ID与课程号不能为空");
        }
        if (coursePersonnelMapper.find(courseId, studentId) == null) {
            throw new BusinessException("您未选修该课程");
        }
        coursePersonnelMapper.delete(courseId, studentId);
    }

    // ==================== 查询 ====================

    /** 查询课程的班级成员(含学生昵称) */
    public List<CourseMemberVO> listMembers(Long courseId) {
        searchCourse(courseId);
        return coursePersonnelMapper.findMembersByCourseId(courseId);
    }

    /** 查询教师创建的全部课程 */
    public List<Course> listTeacherCourses(Long teacherId) {
        if (teacherId == null) {
            throw new BusinessException("教师ID不能为空");
        }
        return courseMapper.findByTeacherId(teacherId);
    }

    /** 查询学生已选的全部课程 */
    public List<Course> listStudentCourses(Long studentId) {
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        List<CoursePersonnel> enrollments = coursePersonnelMapper.findByStudentId(studentId);
        if (enrollments.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = enrollments.stream()
                .map(CoursePersonnel::getCourseId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Course> courseMap = courseMapper.findByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getCourseId, c -> c, (a, b) -> a));
        List<Course> courses = new ArrayList<>();
        for (CoursePersonnel enrollment : enrollments) {
            Course course = courseMap.get(enrollment.getCourseId());
            if (course != null) {
                courses.add(course);
            }
        }
        return courses;
    }

    /**
     * 计算并返回学生的总分(所有已结课课程 student_score 的平均分)。
     */
    public StudentScoreVO getStudentTotalScore(Long studentId) {
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        if (studentMapper.findById(studentId) == null) {
            throw new BusinessException("学生不存在");
        }
        return coursePersonnelMapper.getStudentTotalScore(studentId);
    }

    /**
     * 列出学生所有已选课程各自的得分。
     */
    public List<com.example.bishe.dto.CourseScoreItemVO> listStudentCourseScores(Long studentId) {
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        if (studentMapper.findById(studentId) == null) {
            throw new BusinessException("学生不存在");
        }
        List<CoursePersonnel> enrollments = coursePersonnelMapper.findByStudentId(studentId);
        if (enrollments.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = enrollments.stream()
                .map(CoursePersonnel::getCourseId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Course> courseMap = courseMapper.findByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getCourseId, c -> c, (a, b) -> a));
        List<Long> teacherIds = courseMap.values().stream()
                .map(Course::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> teacherNameMap = teacherIds.isEmpty() ? Map.of()
                : teacherMapper.findByIds(teacherIds).stream()
                .collect(Collectors.toMap(t -> t.getTeacherId(), t -> t.getName(), (a, b) -> a));

        List<com.example.bishe.dto.CourseScoreItemVO> result = new ArrayList<>();
        for (CoursePersonnel enrollment : enrollments) {
            Course course = courseMap.get(enrollment.getCourseId());
            if (course == null) {
                continue;
            }
            com.example.bishe.dto.CourseScoreItemVO item = new com.example.bishe.dto.CourseScoreItemVO();
            item.setCourseId(course.getCourseId());
            item.setCourseName(course.getCourseName());
            item.setTeacherId(course.getTeacherId());
            item.setIsFinished(course.getIsFinished());
            item.setStudentScore(enrollment.getStudentScore());
            if (course.getTeacherId() != null) {
                item.setTeacherName(teacherNameMap.get(course.getTeacherId()));
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 查询学生某门课程的当前得分。
     */
    public com.example.bishe.dto.CourseScoreItemVO getStudentCourseScore(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) {
            throw new BusinessException("学生ID和课程ID不能为空");
        }
        CoursePersonnel enrollment = coursePersonnelMapper.find(courseId, studentId);
        if (enrollment == null) {
            throw new BusinessException("您未选修该课程");
        }
        Course course = courseMapper.findById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        com.example.bishe.dto.CourseScoreItemVO item = new com.example.bishe.dto.CourseScoreItemVO();
        item.setCourseId(course.getCourseId());
        item.setCourseName(course.getCourseName());
        item.setTeacherId(course.getTeacherId());
        item.setIsFinished(course.getIsFinished());
        item.setStudentScore(enrollment.getStudentScore());
        if (course.getTeacherId() != null) {
            var teacher = teacherMapper.findById(course.getTeacherId());
            if (teacher != null) {
                item.setTeacherName(teacher.getName());
            }
        }
        return item;
    }

    /**
     * 更新学生在某课程中的分数(供考试/作业模块调用)。
     */
    @Transactional
    public void updateStudentScore(Long courseId, Long studentId, BigDecimal score) {
        if (courseId == null || studentId == null) {
            throw new BusinessException("课程ID和学生ID不能为空");
        }
        if (coursePersonnelMapper.find(courseId, studentId) == null) {
            throw new BusinessException("该学生不在此课程中");
        }
        coursePersonnelMapper.updateStudentScore(courseId, studentId, score);
    }

    // ==================== 学生课程分数重算 ====================

    /** 考试权重 */
    private static final BigDecimal EXAM_WEIGHT = new BigDecimal("0.60");
    /** 作业权重 */
    private static final BigDecimal HOMEWORK_WEIGHT = new BigDecimal("0.30");
    /** 签到权重 */
    private static final BigDecimal ATTENDANCE_WEIGHT = new BigDecimal("0.10");
    /** 百分制满分 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * 重新计算并更新某学生在某课程中的分数。
     * 分数公式: 考试占60%、作业占30%、签到占10%, 各部分归一化到百分制后加权求和。
     * 若某部分无数据, 其权重按比例重新分配给其余部分。
     */
    @Transactional
    public void recalculateAndUpdateStudentScore(Long courseId, Long studentId) {
        if (courseId == null || studentId == null) {
            throw new BusinessException("课程ID和学生ID不能为空");
        }
        if (coursePersonnelMapper.find(courseId, studentId) == null) {
            throw new BusinessException("该学生不在此课程中");
        }

        // 1. 考试部分
        BigDecimal examScoreSum = coursePersonnelMapper.getExamScoreSumByCourseAndStudent(courseId, studentId);
        BigDecimal examMaxSum = coursePersonnelMapper.getExamMaxScoreSumByCourse(courseId);
        boolean hasExam = examMaxSum != null && examMaxSum.compareTo(BigDecimal.ZERO) > 0;

        // 2. 作业部分
        BigDecimal hwScoreSum = coursePersonnelMapper.getHomeworkScoreSumByCourseAndStudent(courseId, studentId);
        BigDecimal hwMaxSum = coursePersonnelMapper.getHomeworkMaxScoreSumByCourse(courseId);
        boolean hasHomework = hwMaxSum != null && hwMaxSum.compareTo(BigDecimal.ZERO) > 0;

        // 3. 签到部分(按签到分值加权: 已得签到分 / 签到满分)
        Map<String, Object> attStats = coursePersonnelMapper.getAttendanceStatsByCourseAndStudent(courseId, studentId);
        BigDecimal attTotalScore = BigDecimal.ZERO;
        BigDecimal attEarnedScore = BigDecimal.ZERO;
        if (attStats != null) {
            Object totalScoreVal = attStats.get("totalScore");
            if (totalScoreVal == null) {
                totalScoreVal = attStats.get("totalscore");
            }
            Object earnedScoreVal = attStats.get("earnedScore");
            if (earnedScoreVal == null) {
                earnedScoreVal = attStats.get("earnedscore");
            }
            if (totalScoreVal != null) {
                attTotalScore = new BigDecimal(totalScoreVal.toString());
            }
            if (earnedScoreVal != null) {
                attEarnedScore = new BigDecimal(earnedScoreVal.toString());
            }
        }
        boolean hasAttendance = attTotalScore.compareTo(BigDecimal.ZERO) > 0;

        // 计算各部分的百分制得分
        BigDecimal examPct = BigDecimal.ZERO;
        BigDecimal hwPct = BigDecimal.ZERO;
        BigDecimal attPct = BigDecimal.ZERO;

        if (hasExam) {
            examPct = examScoreSum.multiply(HUNDRED).divide(examMaxSum, 2, RoundingMode.HALF_UP);
        }
        if (hasHomework) {
            hwPct = hwScoreSum.multiply(HUNDRED).divide(hwMaxSum, 2, RoundingMode.HALF_UP);
        }
        if (hasAttendance) {
            attPct = attEarnedScore.multiply(HUNDRED).divide(attTotalScore, 2, RoundingMode.HALF_UP);
        }

        // 动态调整权重: 若某部分无数据则按比例分配其权重到其余部分
        BigDecimal activeWeight = BigDecimal.ZERO;
        if (hasExam) activeWeight = activeWeight.add(EXAM_WEIGHT);
        if (hasHomework) activeWeight = activeWeight.add(HOMEWORK_WEIGHT);
        if (hasAttendance) activeWeight = activeWeight.add(ATTENDANCE_WEIGHT);

        BigDecimal studentScore;
        if (activeWeight.compareTo(BigDecimal.ZERO) == 0) {
            // 没有任何数据
            studentScore = BigDecimal.ZERO;
        } else {
            BigDecimal totalScore = BigDecimal.ZERO;
            if (hasExam) {
                totalScore = totalScore.add(examPct.multiply(EXAM_WEIGHT));
            }
            if (hasHomework) {
                totalScore = totalScore.add(hwPct.multiply(HOMEWORK_WEIGHT));
            }
            if (hasAttendance) {
                totalScore = totalScore.add(attPct.multiply(ATTENDANCE_WEIGHT));
            }
            // 归一化: totalScore / activeWeight (等于乘以 1/activeWeight)
            studentScore = totalScore.divide(activeWeight, 1, RoundingMode.HALF_UP);
        }

        coursePersonnelMapper.updateStudentScore(courseId, studentId, studentScore);
    }

    /**
     * 重新计算并更新某学生的综合评分(所有已结课课程 student_score 的平均分)。
     * 若学生尚无已结课课程, 综合评分设为 null。
     */
    @Transactional
    public void updateComprehensiveScoreForStudent(Long studentId) {
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        if (studentMapper.findById(studentId) == null) {
            throw new BusinessException("学生不存在");
        }
        StudentScoreVO scoreVO = coursePersonnelMapper.getStudentTotalScore(studentId);
        BigDecimal comprehensiveScore = null;
        if (scoreVO != null && scoreVO.getFinishedCourseCount() > 0) {
            comprehensiveScore = scoreVO.getTotalScore();
        }
        // comprehensiveScore 为 null 表示暂无已结课课程
        studentMapper.updateComprehensiveScore(studentId, comprehensiveScore);
    }

    /**
     * 对某课程下所有学生重新计算综合评分(课程状态变更时使用)。
     */
    private void updateComprehensiveScoreForAllStudentsInCourse(Long courseId) {
        List<CoursePersonnel> members = coursePersonnelMapper.findByCourseId(courseId);
        for (CoursePersonnel member : members) {
            updateComprehensiveScoreForStudent(member.getStudentId());
        }
    }

    // ==================== 供管理员复用的级联删除 ====================

    /**
     * 级联删除单个课程下的所有关联记录(考试、作业、签到及其明细、选课记录), 最后删除课程本身。
     * 注意: 本方法不维护 teacher.course_count, 由调用方决定。
     */
    @Transactional
    public void deleteCourseCascade(Long courseId) {
        // 考试相关(先删依赖考试的明细, 再删考试)
        cascadeMapper.deleteExamAnswersByCourseId(courseId);
        cascadeMapper.deleteExamQuestionsByCourseId(courseId);
        cascadeMapper.deleteUserExamRecordsByCourseId(courseId);
        cascadeMapper.deleteExamsByCourseId(courseId);

        // 作业相关
        cascadeMapper.deleteHomeworkAnswersByCourseId(courseId);
        cascadeMapper.deleteHomeworkQuestionsByCourseId(courseId);
        cascadeMapper.deleteUserHomeworkRecordsByCourseId(courseId);
        cascadeMapper.deleteHomeworksByCourseId(courseId);

        // 签到相关
        cascadeMapper.deleteCheckInUsersByCourseId(courseId);
        cascadeMapper.deleteCheckInRecordsByCourseId(courseId);

        // 选课记录与课程本身
        coursePersonnelMapper.deleteByCourseId(courseId);
        courseMapper.deleteById(courseId);
    }

    /**
     * 校验课程存在且归属于指定教师, 返回课程。
     */
    private Course requireOwnedCourse(Long teacherId, Long courseId) {
        if (teacherId == null || courseId == null) {
            throw new BusinessException("教师ID与课程ID不能为空");
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
}

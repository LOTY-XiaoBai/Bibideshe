package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.dto.CourseTeacherVO;
import com.example.bishe.dto.TeacherRateRequest;
import com.example.bishe.dto.TeacherScoreVO;
import com.example.bishe.entity.Course;
import com.example.bishe.entity.CoursePersonnel;
import com.example.bishe.entity.Teacher;
import com.example.bishe.entity.TeacherEvaluation;
import com.example.bishe.mapper.CourseMapper;
import com.example.bishe.mapper.CoursePersonnelMapper;
import com.example.bishe.mapper.TeacherEvaluationMapper;
import com.example.bishe.mapper.TeacherMapper;
import com.example.bishe.mapper.TeacherRatingCount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 教师评分服务: 学生只能给教过自己的课程的老师打 1-10 分, 平均分回写教师综合评分。
 */
@Service
public class TeacherEvaluationService {

    private static final BigDecimal MIN_SCORE = BigDecimal.ONE;
    private static final BigDecimal MAX_SCORE = new BigDecimal("10");

    private final TeacherEvaluationMapper teacherEvaluationMapper;
    private final TeacherMapper teacherMapper;
    private final CourseMapper courseMapper;
    private final CoursePersonnelMapper coursePersonnelMapper;

    public TeacherEvaluationService(TeacherEvaluationMapper teacherEvaluationMapper,
                                    TeacherMapper teacherMapper,
                                    CourseMapper courseMapper,
                                    CoursePersonnelMapper coursePersonnelMapper) {
        this.teacherEvaluationMapper = teacherEvaluationMapper;
        this.teacherMapper = teacherMapper;
        this.courseMapper = courseMapper;
        this.coursePersonnelMapper = coursePersonnelMapper;
    }

    /**
     * 学生通过已结课的选修课程给主讲教师打分，同一学生对同一教师仅可评分一次。
     */
    @Transactional
    public TeacherScoreVO rateTeacher(Long studentId, TeacherRateRequest request) {
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        if (request == null || request.getCourseId() == null || request.getTeacherId() == null) {
            throw new BusinessException("课程ID和教师ID不能为空");
        }
        BigDecimal score = request.getScore();
        if (score == null) {
            throw new BusinessException("评分不能为空");
        }
        if (score.compareTo(MIN_SCORE) < 0 || score.compareTo(MAX_SCORE) > 0) {
            throw new BusinessException("评分范围为1-10分");
        }
        // 允许整数或一位小数
        score = score.setScale(1, RoundingMode.HALF_UP);

        Course course = courseMapper.findByIdForUpdate(request.getCourseId());
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (!Integer.valueOf(1).equals(course.getIsFinished())) throw new BusinessException("课程结课后才能给教师打分");
        if (!course.getTeacherId().equals(request.getTeacherId())) {
            throw new BusinessException("该教师不是本课程主讲教师");
        }
        CoursePersonnel enrollment = coursePersonnelMapper.find(request.getCourseId(), studentId);
        if (enrollment == null) {
            throw new BusinessException("您未选修该课程, 不能给该教师打分");
        }
        Teacher teacher = teacherMapper.findById(request.getTeacherId());
        if (teacher == null) {
            throw new BusinessException("教师不存在");
        }

        TeacherEvaluation existing = teacherEvaluationMapper.findByTeacherAndStudent(
                request.getTeacherId(), studentId);
        if (existing == null) {
            TeacherEvaluation evaluation = new TeacherEvaluation();
            evaluation.setTeacherId(request.getTeacherId());
            evaluation.setStudentId(studentId);
            evaluation.setCourseId(request.getCourseId());
            evaluation.setScore(score);
            teacherEvaluationMapper.insert(evaluation);
        } else {
            throw new BusinessException("您已给该教师打分，不能重复评分");
        }

        refreshTeacherComprehensiveScore(request.getTeacherId());
        return getTeacherScore(request.getTeacherId());
    }

    /**
     * 查看某门课程的教师评分信息(学生点进课程看老师)。
     */
    public CourseTeacherVO getCourseTeacher(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) {
            throw new BusinessException("学生ID和课程ID不能为空");
        }
        Course course = courseMapper.findById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (coursePersonnelMapper.find(courseId, studentId) == null) {
            throw new BusinessException("您未选修该课程");
        }
        Teacher teacher = teacherMapper.findById(course.getTeacherId());
        if (teacher == null) {
            throw new BusinessException("教师不存在");
        }

        CourseTeacherVO vo = new CourseTeacherVO();
        vo.setCourseId(course.getCourseId());
        vo.setCourseName(course.getCourseName());
        vo.setIsFinished(course.getIsFinished());
        vo.setTeacherId(teacher.getTeacherId());
        vo.setTeacherName(teacher.getName());
        vo.setTeacherAvatar(teacher.getAvatar());
        vo.setTeacherSignature(teacher.getSignature());

        int count = teacherEvaluationMapper.countByTeacherId(teacher.getTeacherId());
        if (count <= 0) {
            vo.setComprehensiveScore(null);
            vo.setHasRating(false);
            vo.setScoreTip("暂未有人打分");
        } else {
            vo.setComprehensiveScore(teacher.getComprehensiveScore());
            vo.setHasRating(true);
            vo.setScoreTip(null);
        }

        TeacherEvaluation mine = teacherEvaluationMapper.findByTeacherAndStudent(
                teacher.getTeacherId(), studentId);
        vo.setMyScore(mine == null ? null : mine.getScore());
        return vo;
    }

    /**
     * 列出当前学生可评分的课程教师(自己已选的全部课程)。
     */
    public List<CourseTeacherVO> listRateableTeachers(Long studentId) {
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
        List<Long> teacherIds = courseMap.values().stream()
                .map(Course::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Teacher> teacherMap = teacherIds.isEmpty() ? Map.of()
                : teacherMapper.findByIds(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getTeacherId, t -> t, (a, b) -> a));
        Map<Long, Integer> ratingCountMap = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            for (TeacherRatingCount count : teacherEvaluationMapper.countGroupedByTeacherIds(teacherIds)) {
                ratingCountMap.put(count.getTeacherId(), count.getRatingCount());
            }
        }
        Map<Long, BigDecimal> myScoreMap = teacherEvaluationMapper.findByStudentId(studentId).stream()
                .collect(Collectors.toMap(TeacherEvaluation::getTeacherId, TeacherEvaluation::getScore, (a, b) -> a));

        List<CourseTeacherVO> result = new ArrayList<>();
        for (CoursePersonnel enrollment : enrollments) {
            Course course = courseMap.get(enrollment.getCourseId());
            if (course == null || course.getTeacherId() == null) {
                continue;
            }
            Teacher teacher = teacherMap.get(course.getTeacherId());
            if (teacher == null) {
                continue;
            }
            CourseTeacherVO vo = new CourseTeacherVO();
            vo.setCourseId(course.getCourseId());
            vo.setCourseName(course.getCourseName());
            vo.setIsFinished(course.getIsFinished());
            vo.setTeacherId(teacher.getTeacherId());
            vo.setTeacherName(teacher.getName());
            vo.setTeacherAvatar(teacher.getAvatar());
            vo.setTeacherSignature(teacher.getSignature());
            int count = ratingCountMap.getOrDefault(teacher.getTeacherId(), 0);
            if (count <= 0) {
                vo.setComprehensiveScore(null);
                vo.setHasRating(false);
                vo.setScoreTip("暂未有人打分");
            } else {
                vo.setComprehensiveScore(teacher.getComprehensiveScore());
                vo.setHasRating(true);
                vo.setScoreTip(null);
            }
            vo.setMyScore(myScoreMap.get(teacher.getTeacherId()));
            result.add(vo);
        }
        return result;
    }

    public TeacherScoreVO getTeacherScore(Long teacherId) {
        Teacher teacher = teacherMapper.findById(teacherId);
        if (teacher == null) {
            throw new BusinessException("教师不存在");
        }
        int count = teacherEvaluationMapper.countByTeacherId(teacherId);
        TeacherScoreVO vo = new TeacherScoreVO();
        vo.setTeacherId(teacherId);
        vo.setTeacherName(teacher.getName());
        vo.setRatingCount(count);
        if (count <= 0) {
            vo.setComprehensiveScore(null);
            vo.setHasRating(false);
            vo.setScoreTip("暂未有人打分");
        } else {
            vo.setComprehensiveScore(teacher.getComprehensiveScore());
            vo.setHasRating(true);
            vo.setScoreTip(null);
        }
        return vo;
    }

    @Transactional
    public void refreshTeacherComprehensiveScore(Long teacherId) {
        int count = teacherEvaluationMapper.countByTeacherId(teacherId);
        if (count <= 0) {
            teacherMapper.updateComprehensiveScore(teacherId, BigDecimal.ZERO);
            return;
        }
        BigDecimal avg = teacherEvaluationMapper.avgScoreByTeacherId(teacherId);
        if (avg == null) {
            avg = BigDecimal.ZERO;
        } else {
            avg = avg.setScale(2, RoundingMode.HALF_UP);
        }
        teacherMapper.updateComprehensiveScore(teacherId, avg);
    }
}

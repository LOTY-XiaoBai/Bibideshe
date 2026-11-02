package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.common.UserRole;
import com.example.bishe.dto.UserProfileVO;
import com.example.bishe.entity.Student;
import com.example.bishe.entity.Teacher;
import com.example.bishe.mapper.StudentMapper;
import com.example.bishe.mapper.TeacherEvaluationMapper;
import com.example.bishe.mapper.TeacherMapper;
import com.example.bishe.mapper.TeacherRatingCount;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 交际模块共用: 按用户ID解析学生/教师公开资料。
 */
@Service
public class SocialUserService {

    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final TeacherEvaluationMapper teacherEvaluationMapper;

    public SocialUserService(StudentMapper studentMapper,
                             TeacherMapper teacherMapper,
                             TeacherEvaluationMapper teacherEvaluationMapper) {
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.teacherEvaluationMapper = teacherEvaluationMapper;
    }

    public UserProfileVO requireProfile(Long userId) {
        UserProfileVO profile = findProfile(userId);
        if (profile == null) {
            throw new BusinessException("用户不存在: " + userId);
        }
        return profile;
    }

    public UserProfileVO findProfile(Long userId) {
        if (userId == null) {
            return null;
        }
        return findProfiles(List.of(userId)).get(userId);
    }

    /**
     * 批量解析用户公开资料, 避免列表场景下的 N+1。
     * 返回 map 的 key 为 userId; 不存在的用户不会出现在 map 中。
     */
    public Map<Long, UserProfileVO> findProfiles(Collection<Long> userIds) {
        Map<Long, UserProfileVO> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return result;
        }

        Set<Long> remaining = new LinkedHashSet<>(ids);
        List<Student> students = studentMapper.findByIds(ids);
        for (Student student : students) {
            UserProfileVO vo = new UserProfileVO();
            vo.setUserId(student.getStudentId());
            vo.setRole(UserRole.STUDENT);
            vo.setDisplayName(student.getNickname());
            vo.setSignature(student.getSignature());
            vo.setAvatar(student.getAvatar());
            vo.setComprehensiveScore(student.getComprehensiveScore());
            result.put(student.getStudentId(), vo);
            remaining.remove(student.getStudentId());
        }

        if (remaining.isEmpty()) {
            return result;
        }
        List<Long> teacherIds = new ArrayList<>(remaining);
        List<Teacher> teachers = teacherMapper.findByIds(teacherIds);
        Map<Long, Integer> ratingCountMap = new HashMap<>();
        if (!teachers.isEmpty()) {
            List<Long> foundTeacherIds = teachers.stream().map(Teacher::getTeacherId).collect(Collectors.toList());
            for (TeacherRatingCount count : teacherEvaluationMapper.countGroupedByTeacherIds(foundTeacherIds)) {
                ratingCountMap.put(count.getTeacherId(), count.getRatingCount());
            }
        }
        for (Teacher teacher : teachers) {
            UserProfileVO vo = new UserProfileVO();
            vo.setUserId(teacher.getTeacherId());
            vo.setRole(UserRole.TEACHER);
            vo.setDisplayName(teacher.getName());
            vo.setSignature(teacher.getSignature());
            vo.setAvatar(teacher.getAvatar());
            int ratingCount = ratingCountMap.getOrDefault(teacher.getTeacherId(), 0);
            if (ratingCount <= 0) {
                vo.setComprehensiveScore(null);
                vo.setHasRating(false);
                vo.setScoreTip("暂未有人打分");
            } else {
                vo.setComprehensiveScore(teacher.getComprehensiveScore());
                vo.setHasRating(true);
                vo.setScoreTip(null);
            }
            result.put(teacher.getTeacherId(), vo);
        }
        return result;
    }

    public boolean exists(Long userId) {
        return findProfile(userId) != null;
    }

    /**
     * 按昵称(学生)或姓名(教师)模糊搜索, 返回全部匹配用户。
     */
    public List<UserProfileVO> searchByDisplayName(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String kw = keyword.trim();
        List<UserProfileVO> result = new ArrayList<>();
        List<Long> teacherIds = new ArrayList<>();

        for (Student student : studentMapper.searchByNickname(kw)) {
            UserProfileVO vo = new UserProfileVO();
            vo.setUserId(student.getStudentId());
            vo.setRole(UserRole.STUDENT);
            vo.setDisplayName(student.getNickname());
            vo.setSignature(student.getSignature());
            vo.setAvatar(student.getAvatar());
            vo.setComprehensiveScore(student.getComprehensiveScore());
            result.add(vo);
        }

        List<Teacher> teachers = teacherMapper.searchByName(kw);
        for (Teacher teacher : teachers) {
            teacherIds.add(teacher.getTeacherId());
        }
        Map<Long, Integer> ratingCountMap = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            for (TeacherRatingCount count : teacherEvaluationMapper.countGroupedByTeacherIds(teacherIds)) {
                ratingCountMap.put(count.getTeacherId(), count.getRatingCount());
            }
        }
        for (Teacher teacher : teachers) {
            UserProfileVO vo = new UserProfileVO();
            vo.setUserId(teacher.getTeacherId());
            vo.setRole(UserRole.TEACHER);
            vo.setDisplayName(teacher.getName());
            vo.setSignature(teacher.getSignature());
            vo.setAvatar(teacher.getAvatar());
            int ratingCount = ratingCountMap.getOrDefault(teacher.getTeacherId(), 0);
            if (ratingCount <= 0) {
                vo.setComprehensiveScore(null);
                vo.setHasRating(false);
                vo.setScoreTip("暂未有人打分");
            } else {
                vo.setComprehensiveScore(teacher.getComprehensiveScore());
                vo.setHasRating(true);
                vo.setScoreTip(null);
            }
            result.add(vo);
        }
        return result;
    }
}

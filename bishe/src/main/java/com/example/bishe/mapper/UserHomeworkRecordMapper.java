package com.example.bishe.mapper;

import com.example.bishe.entity.UserHomeworkRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户作业总记录表数据访问接口 (表19 user_homework_record)。
 */
@Mapper
public interface UserHomeworkRecordMapper {

    /** 新增一条作业总记录 */
    int insert(UserHomeworkRecord record);

    /** 查询某学生某作业的总记录 */
    UserHomeworkRecord find(@Param("studentId") Long studentId, @Param("homeworkId") Long homeworkId);

    /** 查询某学生的全部作业记录 */
    List<UserHomeworkRecord> findByStudentId(Long studentId);

    /** 查询某作业的全部学生记录 */
    List<UserHomeworkRecord> findByHomeworkId(Long homeworkId);

    /** 更新作业自动得分(题目分数汇总) */
    int updateHomeworkScore(@Param("studentId") Long studentId, @Param("homeworkId") Long homeworkId,
                             @Param("homeworkScore") BigDecimal homeworkScore);

    /** 更新教师评分 */
    int updateTeacherScore(@Param("studentId") Long studentId, @Param("homeworkId") Long homeworkId,
                            @Param("teacherScore") BigDecimal teacherScore);

    /** 按课程删除 */
    int deleteByCourseId(Long courseId);

    /** 按学生删除 */
    int deleteByStudentId(Long studentId);

    /** 按发布者删除(经由作业反查) */
    int deleteByPublisherId(Long publisherId);

    /** 删除某学生某作业的总记录(覆盖提交通用) */
    int deleteByStudentAndHomework(@Param("studentId") Long studentId, @Param("homeworkId") Long homeworkId);
}

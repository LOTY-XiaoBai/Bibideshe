package com.example.bishe.mapper;

import com.example.bishe.entity.UserExamRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户考试总记录表数据访问接口 (表16 user_exam_record)。
 */
@Mapper
public interface UserExamRecordMapper {

    /** 新增一条考试总记录 */
    int insert(UserExamRecord record);

    /** 查询某学生某考试的总记录 */
    UserExamRecord find(@Param("studentId") Long studentId, @Param("examId") Long examId);

    /** 查询某学生的全部考试记录 */
    List<UserExamRecord> findByStudentId(Long studentId);

    /** 查询某考试的全部学生记录 */
    List<UserExamRecord> findByExamId(Long examId);

    /** 更新考试自动得分(题目分数汇总) */
    int updateExamScore(@Param("studentId") Long studentId, @Param("examId") Long examId,
                         @Param("examScore") java.math.BigDecimal examScore);

    /** 更新教师评分 */
    int updateTeacherScore(@Param("studentId") Long studentId, @Param("examId") Long examId,
                            @Param("teacherScore") java.math.BigDecimal teacherScore);

    /** 按课程删除 */
    int deleteByCourseId(Long courseId);

    /** 按学生删除 */
    int deleteByStudentId(Long studentId);

    /** 按发布者删除(经由考试反查) */
    int deleteByPublisherId(Long publisherId);

    /** 删除某学生某考试的总记录(覆盖提交通用) */
    int deleteByStudentAndExam(@Param("studentId") Long studentId, @Param("examId") Long examId);

    /** 更新得分(保留 start_time 不变) */
    int updateScores(@Param("studentId") Long studentId, @Param("examId") Long examId,
                     @Param("examScore") java.math.BigDecimal examScore,
                     @Param("teacherScore") java.math.BigDecimal teacherScore);

    /** 补写开始时间(兼容旧数据) */
    int updateStartTime(@Param("studentId") Long studentId, @Param("examId") Long examId,
                        @Param("startTime") java.time.LocalDateTime startTime);
}

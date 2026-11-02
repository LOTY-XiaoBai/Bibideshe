package com.example.bishe.mapper;

import com.example.bishe.entity.Exam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 考试表数据访问接口 (表14 exam)。
 */
@Mapper
public interface ExamMapper {

    /** 新增考试, 自增主键回填到实体 examId */
    int insert(Exam exam);

    /** 按考试ID查询 */
    Exam findById(Long examId);

    /** 按课程ID查询该课程下的全部考试 */
    List<Exam> findByCourseId(Long courseId);

    /** 按发布者(教师ID)查询其发布的全部考试 */
    List<Exam> findByPublisherId(Long publisherId);

    /** 按考试ID删除 */
    int deleteById(Long examId);

    /** 按课程ID删除 */
    int deleteByCourseId(Long courseId);

    /** 按发布者删除 */
    int deleteByPublisherId(Long publisherId);

    /** 更新成绩公布状态 */
    int updateResultPublished(@Param("examId") Long examId, @Param("isResultPublished") int isResultPublished);
}

package com.example.bishe.mapper;

import com.example.bishe.entity.Homework;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 作业表数据访问接口 (表17 homework)。
 */
@Mapper
public interface HomeworkMapper {

    /** 新增作业, 自增主键回填到实体 homeworkId */
    int insert(Homework homework);

    /** 按作业ID查询 */
    Homework findById(Long homeworkId);

    /** 按课程ID查询该课程下的全部作业 */
    List<Homework> findByCourseId(Long courseId);

    /** 按发布者(教师ID)查询其发布的全部作业 */
    List<Homework> findByPublisherId(Long publisherId);

    /** 按作业ID删除 */
    int deleteById(Long homeworkId);

    /** 按课程ID删除 */
    int deleteByCourseId(Long courseId);

    /** 按发布者删除 */
    int deleteByPublisherId(Long publisherId);

    /** 更新成绩公布状态 */
    int updateResultPublished(@Param("homeworkId") Long homeworkId, @Param("isResultPublished") int isResultPublished);
}

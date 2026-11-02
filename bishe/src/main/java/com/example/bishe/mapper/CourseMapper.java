package com.example.bishe.mapper;

import com.example.bishe.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 课程表数据访问接口 (表21 course)。
 */
@Mapper
public interface CourseMapper {

    /** 新增课程, 自增主键回填到实体 courseId */
    int insert(Course course);

    /** 按课程ID查询课程 */
    Course findById(Long courseId);

    /** 按课程ID列表批量查询 */
    List<Course> findByIds(@Param("ids") List<Long> ids);

    /** 按主讲教师查询其全部课程 */
    List<Course> findByTeacherId(Long teacherId);

    /** 统计某教师已创建的课程数量 */
    int countByTeacherId(Long teacherId);

    /** 统计某教师未结课的课程数量(用于创建上限校验) */
    int countActiveByTeacherId(Long teacherId);

    /** 更新课程结束状态 */
    int updateFinished(@Param("courseId") Long courseId, @Param("isFinished") int isFinished);

    /** 按课程ID删除课程 */
    int deleteById(Long courseId);

    /** 按教师ID删除其全部课程 */
    int deleteByTeacherId(Long teacherId);

    /** 管理员分页查询全部课程(可按课程号精确筛选) */
    List<Course> findForAdmin(@Param("courseId") Long courseId,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    /** 管理员课程总数(可按课程号精确筛选) */
    int countForAdmin(@Param("courseId") Long courseId);
}

package com.example.bishe.mapper;

import com.example.bishe.entity.HomeworkQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 作业题目关联表数据访问接口 (表18 homework_question)。
 */
@Mapper
public interface HomeworkQuestionMapper {

    /** 批量插入组卷题目 */
    int batchInsert(List<HomeworkQuestion> questions);

    /** 查询某作业的全部题目 */
    List<HomeworkQuestion> findByHomeworkId(Long homeworkId);

    /** 查询某作业中某道题的分值配置 */
    HomeworkQuestion find(@Param("homeworkId") Long homeworkId,
                           @Param("questionId") Long questionId,
                           @Param("questionType") Integer questionType);

    /** 查询引用了某题目(题型)的全部作业题目配置 */
    List<HomeworkQuestion> findByQuestion(@Param("questionId") Long questionId,
                                           @Param("questionType") Integer questionType);

    /** 删除某作业的全部题目配置 */
    int deleteByHomeworkId(Long homeworkId);

    /** 按课程删除 */
    int deleteByCourseId(Long courseId);

    /** 按发布者删除 */
    int deleteByPublisherId(Long publisherId);
}

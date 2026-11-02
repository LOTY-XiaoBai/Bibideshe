package com.example.bishe.mapper;

import com.example.bishe.entity.ExamQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 考试题目关联表数据访问接口 (表15 exam_question)。
 */
@Mapper
public interface ExamQuestionMapper {

    /** 批量插入组卷题目 */
    int batchInsert(List<ExamQuestion> questions);

    /** 查询某考试的全部题目(按插入顺序) */
    List<ExamQuestion> findByExamId(Long examId);

    /** 查询某考试中某道题的分值配置 */
    ExamQuestion find(@Param("examId") Long examId,
                       @Param("questionId") Long questionId,
                       @Param("questionType") Integer questionType);

    /** 查询引用了某题目(题型)的全部考试题目配置(用于题目被删除时定位受影响的考试) */
    List<ExamQuestion> findByQuestion(@Param("questionId") Long questionId,
                                       @Param("questionType") Integer questionType);

    /** 删除某考试的全部题目配置 */
    int deleteByExamId(Long examId);

    /** 按课程删除(级联课程下所有考试的题目配置) */
    int deleteByCourseId(Long courseId);

    /** 按发布者删除 */
    int deleteByPublisherId(Long publisherId);
}

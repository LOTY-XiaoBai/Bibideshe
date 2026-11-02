package com.example.bishe.mapper;

import com.example.bishe.entity.ExamQuestionAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 考试题目答题情况表数据访问接口 (表15.1 exam_question_answer)。
 */
@Mapper
public interface ExamQuestionAnswerMapper {

    /** 插入一条答题记录 */
    int insert(ExamQuestionAnswer answer);

    /** 更新一条答题记录的分数与批注(题目被删除时补满分等场景使用) */
    int updateScore(ExamQuestionAnswer answer);

    /** 查询某学生在某考试的全部答题记录 */
    List<ExamQuestionAnswer> findByExamAndStudent(@Param("examId") Long examId, @Param("studentId") Long studentId);

    /** 查询某学生是否已经作答过某考试某题 */
    ExamQuestionAnswer find(@Param("examId") Long examId,
                             @Param("questionId") Long questionId,
                             @Param("questionType") Integer questionType,
                             @Param("studentId") Long studentId);

    /** 查询某学生是否已提交过该考试(存在任意一条答题记录即视为已提交) */
    int countByExamAndStudent(@Param("examId") Long examId, @Param("studentId") Long studentId);

    /** 查询引用了某题目(题型)的全部答题记录(题目被删除时用于补发满分) */
    List<ExamQuestionAnswer> findByQuestion(@Param("questionId") Long questionId,
                                             @Param("questionType") Integer questionType);

    /** 查询某考试下待教师批改的解答题 */
    List<ExamQuestionAnswer> findPendingSolveByExam(Long examId);

    /** 汇总某学生在某考试的全部题目得分(用于重新计算总分) */
    java.math.BigDecimal sumScoreByExamAndStudent(@Param("examId") Long examId, @Param("studentId") Long studentId);

    /** 按课程删除 */
    int deleteByCourseId(Long courseId);

    /** 按学生删除 */
    int deleteByStudentId(Long studentId);

    /** 按发布者删除(经由考试反查) */
    int deleteByPublisherId(Long publisherId);

    /** 删除某学生在某考试的全部答题记录(覆盖提交通用) */
    int deleteByExamAndStudent(@Param("examId") Long examId, @Param("studentId") Long studentId);
}

package com.example.bishe.mapper;

import com.example.bishe.entity.HomeworkQuestionAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 作业题目答题情况表数据访问接口 (表18.1 homework_question_answer)。
 */
@Mapper
public interface HomeworkQuestionAnswerMapper {

    /** 插入一条答题记录 */
    int insert(HomeworkQuestionAnswer answer);

    /** 更新一条答题记录的分数与批注 */
    int updateScore(HomeworkQuestionAnswer answer);

    /** 查询某学生在某作业的全部答题记录 */
    List<HomeworkQuestionAnswer> findByHomeworkAndStudent(@Param("homeworkId") Long homeworkId,
                                                            @Param("studentId") Long studentId);

    /** 查询某学生某作业某题的答题记录 */
    HomeworkQuestionAnswer find(@Param("homeworkId") Long homeworkId,
                                 @Param("questionId") Long questionId,
                                 @Param("questionType") Integer questionType,
                                 @Param("studentId") Long studentId);

    /** 查询某学生是否已提交过该作业 */
    int countByHomeworkAndStudent(@Param("homeworkId") Long homeworkId, @Param("studentId") Long studentId);

    /** 查询引用了某题目(题型)的全部答题记录 */
    List<HomeworkQuestionAnswer> findByQuestion(@Param("questionId") Long questionId,
                                                 @Param("questionType") Integer questionType);

    /** 查询某作业下待教师批改的解答题 */
    List<HomeworkQuestionAnswer> findPendingSolveByHomework(Long homeworkId);

    /** 汇总某学生在某作业的全部题目得分(用于重新计算总分) */
    java.math.BigDecimal sumScoreByHomeworkAndStudent(@Param("homeworkId") Long homeworkId,
                                                        @Param("studentId") Long studentId);

    /** 按课程删除 */
    int deleteByCourseId(Long courseId);

    /** 按学生删除 */
    int deleteByStudentId(Long studentId);

    /** 按发布者删除(经由作业反查) */
    int deleteByPublisherId(Long publisherId);

    /** 删除某学生在某作业的全部答题记录(覆盖提交通用) */
    int deleteByHomeworkAndStudent(@Param("homeworkId") Long homeworkId, @Param("studentId") Long studentId);
}

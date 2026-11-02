package com.example.bishe.mapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 级联删除数据访问接口。
 *
 * 数据库未声明外键约束, 因此删除课程 / 注销账号时需要手动清理关联表中的记录,
 * 本接口集中管理这些"解绑"删除语句。
 */
@Mapper
public interface CascadeMapper {

    // ============ 课程维度: 删除某课程下的全部业务记录 ============

    /** 删除该课程的考试答题情况 */
    int deleteExamAnswersByCourseId(Long courseId);

    /** 删除该课程下考试关联的题目 */
    int deleteExamQuestionsByCourseId(Long courseId);

    /** 删除该课程下考试的用户考试记录 */
    int deleteUserExamRecordsByCourseId(Long courseId);

    /** 删除该课程的考试 */
    int deleteExamsByCourseId(Long courseId);

    /** 删除该课程的作业答题情况 */
    int deleteHomeworkAnswersByCourseId(Long courseId);

    /** 删除该课程下作业关联的题目 */
    int deleteHomeworkQuestionsByCourseId(Long courseId);

    /** 删除该课程下作业的用户作业记录 */
    int deleteUserHomeworkRecordsByCourseId(Long courseId);

    /** 删除该课程的作业 */
    int deleteHomeworksByCourseId(Long courseId);

    /** 删除该课程签到的学生签到明细 */
    int deleteCheckInUsersByCourseId(Long courseId);

    /** 删除该课程的签到发布记录 */
    int deleteCheckInRecordsByCourseId(Long courseId);

    // ============ 学生维度: 注销学生账号时清理其个人记录 ============

    /** 删除学生的考试答题情况 */
    int deleteExamAnswersByStudentId(Long studentId);

    /** 删除学生的作业答题情况 */
    int deleteHomeworkAnswersByStudentId(Long studentId);

    /** 删除学生的用户考试记录 */
    int deleteUserExamRecordsByStudentId(Long studentId);

    /** 删除学生的用户作业记录 */
    int deleteUserHomeworkRecordsByStudentId(Long studentId);

    /** 删除学生的签到明细 */
    int deleteCheckInUsersByStudentId(Long studentId);

    /** 删除学生的错题本 */
    int deleteErrorBookByStudentId(Long studentId);

    /** 删除买家(学生/教师)的订单评价 */
    int deleteOrderEvaluationsByBuyerId(Long buyerId);

    /** 删除买家(学生/教师)的订单记录 */
    int deleteOrderRecordsByBuyerId(Long buyerId);

    /** 删除学生对教师的评分记录 */
    int deleteTeacherEvaluationsByStudentId(Long studentId);

    // ============ 教师维度: 注销教师账号时清理其非课程记录 ============

    /** 删除教师的评分记录 */
    int deleteTeacherEvaluationsByTeacherId(Long teacherId);

    /** 删除教师所出选择题的选项 */
    int deleteChoiceOptionsByTeacherId(Long teacherId);

    /** 删除教师所出选择题 */
    int deleteChoiceQuestionsByTeacherId(Long teacherId);

    /** 删除教师所出判断题 */
    int deleteJudgeQuestionsByTeacherId(Long teacherId);

    /** 删除教师所出解答题 */
    int deleteAnswerQuestionsByTeacherId(Long teacherId);
}

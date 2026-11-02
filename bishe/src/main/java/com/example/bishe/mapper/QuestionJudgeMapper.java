package com.example.bishe.mapper;

import com.example.bishe.entity.QuestionJudge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 判断题表数据访问接口 (表12 question_judge)。
 */
@Mapper
public interface QuestionJudgeMapper {

    /** 新增判断题, 自增主键回填到实体 questionId */
    int insert(QuestionJudge question);

    /** 按题目ID查询 */
    QuestionJudge findById(Long questionId);

    /** 按题目ID列表批量查询 */
    List<QuestionJudge> findByIds(@Param("ids") List<Long> ids);

    /** 按发布者查询其全部判断题 */
    List<QuestionJudge> findByPublisherId(Long publisherId);

    /** 查询审核通过的公开判断题 */
    List<QuestionJudge> findPublicApproved();

    /** 查询待审核的公开判断题 */
    List<QuestionJudge> findPendingAudit();

    /** 更新审核状态与是否公开 */
    int updateAuditStatus(QuestionJudge question);

    /** 按题目ID删除 */
    int deleteById(Long questionId);

    /** 按发布者删除其全部判断题 */
    int deleteByPublisherId(Long publisherId);
}

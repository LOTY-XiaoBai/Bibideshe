package com.example.bishe.mapper;

import com.example.bishe.entity.QuestionChoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 选择题表数据访问接口 (表11 question_choice)。
 */
@Mapper
public interface QuestionChoiceMapper {

    /** 新增选择题, 自增主键回填到实体 questionId */
    int insert(QuestionChoice question);

    /** 按题目ID查询 */
    QuestionChoice findById(Long questionId);

    /** 按题目ID列表批量查询 */
    List<QuestionChoice> findByIds(@Param("ids") List<Long> ids);

    /** 按发布者查询其全部选择题 */
    List<QuestionChoice> findByPublisherId(Long publisherId);

    /** 查询审核通过的公开选择题 */
    List<QuestionChoice> findPublicApproved();

    /** 查询待审核的公开选择题(已申请公开但未通过审核) */
    List<QuestionChoice> findPendingAudit();

    /** 更新审核状态与是否公开(审核通过/驳回) */
    int updateAuditStatus(QuestionChoice question);

    /** 按题目ID删除 */
    int deleteById(Long questionId);

    /** 按发布者删除其全部选择题 */
    int deleteByPublisherId(Long publisherId);
}

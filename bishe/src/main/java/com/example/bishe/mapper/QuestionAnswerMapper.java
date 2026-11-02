package com.example.bishe.mapper;

import com.example.bishe.entity.QuestionAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 解答题表数据访问接口 (表13 question_answer)。
 */
@Mapper
public interface QuestionAnswerMapper {

    /** 新增解答题, 自增主键回填到实体 questionId */
    int insert(QuestionAnswer question);

    /** 按题目ID查询 */
    QuestionAnswer findById(Long questionId);

    /** 按题目ID列表批量查询 */
    List<QuestionAnswer> findByIds(@Param("ids") List<Long> ids);

    /** 按发布者查询其全部解答题 */
    List<QuestionAnswer> findByPublisherId(Long publisherId);

    /** 查询审核通过的公开解答题 */
    List<QuestionAnswer> findPublicApproved();

    /** 查询待审核的公开解答题 */
    List<QuestionAnswer> findPendingAudit();

    /** 更新审核状态与是否公开 */
    int updateAuditStatus(QuestionAnswer question);

    /** 按题目ID删除 */
    int deleteById(Long questionId);

    /** 按发布者删除其全部解答题 */
    int deleteByPublisherId(Long publisherId);
}

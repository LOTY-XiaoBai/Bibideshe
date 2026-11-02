package com.example.bishe.mapper;

import com.example.bishe.entity.QuestionChoiceOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 选择题选项表数据访问接口 (表11.1 question_choice_option)。
 */
@Mapper
public interface QuestionChoiceOptionMapper {

    /** 批量插入某题目的全部选项 */
    int batchInsert(List<QuestionChoiceOption> options);

    /** 查询某题目的全部选项 */
    List<QuestionChoiceOption> findByQuestionId(Long questionId);

    /** 按题目ID列表批量查询选项 */
    List<QuestionChoiceOption> findByQuestionIds(@Param("ids") List<Long> ids);

    /** 删除某题目的全部选项 */
    int deleteByQuestionId(Long questionId);

    /** 按发布者删除其全部选择题的选项(教师注销级联使用) */
    int deleteByPublisherId(Long publisherId);
}

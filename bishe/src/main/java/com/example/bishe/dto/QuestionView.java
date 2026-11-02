package com.example.bishe.dto;

import lombok.Data;

import java.util.List;

/**
 * 题目详情的统一展示模型(屏蔽选择题/判断题/解答题三张底层表的差异)。
 *
 * 当题目已被管理员删除, 但考试/作业/错题本中仍保留了对它的引用时,
 * lost=true, content 会被替换为占位文案"因为一些原因，该题目丢失了"。
 */
@Data
public class QuestionView {

    /** 题目ID */
    private Long questionId;

    /** 题目类型: 1选择题, 2判断题, 3解答题 */
    private Integer questionType;

    /** 题目内容 */
    private String content;

    /** 答案(展示给学生作答界面时应置为null, 避免提前泄露答案) */
    private String answer;

    /** 发布者ID(教师ID) */
    private Long publisherId;

    /** 是否公开: 0否, 1是 */
    private Integer isPublic;

    /** 审核状态: 0未审核/未公开, 1已通过审核 */
    private Integer auditStatus;

    /** 选择题的选项列表(仅选择题有值) */
    private List<QuestionOptionView> options;

    /** 该题目是否已被删除(丢失), 前端应据此展示占位文案 */
    private boolean lost;

    public static QuestionView lostPlaceholder(Long questionId, Integer questionType) {
        QuestionView view = new QuestionView();
        view.setQuestionId(questionId);
        view.setQuestionType(questionType);
        view.setContent("因为一些原因，该题目丢失了");
        view.setLost(true);
        return view;
    }
}

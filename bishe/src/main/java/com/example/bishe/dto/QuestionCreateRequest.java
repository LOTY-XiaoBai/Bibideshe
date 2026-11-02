package com.example.bishe.dto;

import lombok.Data;

import java.util.List;

/**
 * 教师创建题目请求(做题上传到题库)。
 * questionType 取值参见 {@link com.example.bishe.common.QuestionType}: 1选择题, 2判断题, 3解答题。
 * options 仅在 questionType=1(选择题) 时需要, 表示选项内容列表(如 ["A.北京","B.上海"] 或 直接选项文字)。
 */
@Data
public class QuestionCreateRequest {

    /** 发布者ID(教师ID) */
    private Long publisherId;

    /** 题目类型: 1选择题, 2判断题, 3解答题 */
    private Integer questionType;

    /** 题目内容 */
    private String content;

    /** 答案 */
    private String answer;

    /** 是否申请公开到公共题库: 0否, 1是(需管理员审核通过后才会在公共题库出现) */
    private Integer isPublic;

    /** 选择题的选项内容列表(仅选择题需要, 顺序对应 A/B/C/D...) */
    private List<String> options;
}

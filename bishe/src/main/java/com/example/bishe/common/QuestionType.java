package com.example.bishe.common;

/**
 * 题目类型常量。
 *
 * 选择题/判断题/解答题分别存放在三张独立的题库表中, 且各自使用独立的
 * AUTO_INCREMENT 主键, 题目ID在不同题型之间可能重复, 因此系统内所有引用
 * 题目的地方(组卷、答题、错题本)都必须同时携带 questionId + questionType
 * 才能唯一定位到具体的一道题目。
 */
public final class QuestionType {

    /** 选择题(question_choice) */
    public static final int CHOICE = 1;

    /** 判断题(question_judge) */
    public static final int JUDGE = 2;

    /** 解答题(question_answer) */
    public static final int SOLVE = 3;

    private QuestionType() {
    }

    public static boolean isValid(Integer type) {
        return type != null && (type == CHOICE || type == JUDGE || type == SOLVE);
    }
}

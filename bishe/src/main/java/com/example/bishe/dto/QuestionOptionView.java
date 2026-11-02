package com.example.bishe.dto;

import lombok.Data;

/**
 * 选择题选项展示模型。
 */
@Data
public class QuestionOptionView {

    /** 选项编号(如A,B,C,D) */
    private String optionCode;

    /** 选项内容 */
    private String optionContent;

    public QuestionOptionView() {
    }

    public QuestionOptionView(String optionCode, String optionContent) {
        this.optionCode = optionCode;
        this.optionContent = optionContent;
    }
}

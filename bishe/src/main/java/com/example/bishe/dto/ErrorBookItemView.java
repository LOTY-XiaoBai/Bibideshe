package com.example.bishe.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题本条目展示模型(错题记录 + 题目详情)。
 */
@Data
public class ErrorBookItemView {

    /** 加入错题本的时间 */
    private LocalDateTime addTime;

    /** 题目详情(若题目已被管理员删除, question.lost=true) */
    private QuestionView question;

    /** 题库已删除原题，错题本仍有可供复习的快照。 */
    private boolean archived;
}

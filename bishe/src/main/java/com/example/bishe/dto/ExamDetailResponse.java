package com.example.bishe.dto;

import com.example.bishe.entity.Exam;
import lombok.Data;

import java.util.List;

/**
 * 考试详情响应(考试信息 + 题目列表)。
 */
@Data
public class ExamDetailResponse {

    /** 考试基本信息 */
    private Exam exam;

    /** 题目列表 */
    private List<QuestionItemView> questions;
}

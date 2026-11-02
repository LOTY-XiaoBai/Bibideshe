package com.example.bishe.dto;

import com.example.bishe.entity.Homework;
import lombok.Data;

import java.util.List;

/**
 * 作业详情响应(作业信息 + 题目列表)。
 */
@Data
public class HomeworkDetailResponse {

    /** 作业基本信息 */
    private Homework homework;

    /** 题目列表 */
    private List<QuestionItemView> questions;
}

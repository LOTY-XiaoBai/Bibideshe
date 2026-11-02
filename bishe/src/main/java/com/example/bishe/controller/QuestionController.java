package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.QuestionCreateRequest;
import com.example.bishe.dto.QuestionView;
import com.example.bishe.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题库相关接口(教师端: 做题上传到题库)。
 */
@Tag(name = "题库", description = "教师创建与管理题库题目")
@RestController
@RequestMapping("/api/question")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /** 教师创建一道题目(选择题/判断题/解答题)并存入题库 */
    @Operation(summary = "教师创建题目并存入题库")
    @PostMapping("/create")
    public Result<QuestionView> create(@RequestBody QuestionCreateRequest request) {
        Long teacherId = AuthContext.requireTeacherId();
        request.setPublisherId(teacherId);
        return Result.success("题目已上传到题库", questionService.createQuestion(request));
    }

    /** 查询当前教师自己上传的全部题目 */
    @Operation(summary = "查询当前教师上传的全部题目")
    @GetMapping("/mine")
    public Result<List<QuestionView>> mine() {
        Long teacherId = AuthContext.requireTeacherId();
        return Result.success(questionService.listMine(teacherId));
    }

    /** 查询审核通过的公共题库题目(供教师组卷时挑选) */
    @Operation(summary = "查询审核通过的公共题库题目")
    @GetMapping("/public")
    public Result<List<QuestionView>> publicQuestions() {
        AuthContext.requireLogin();
        return Result.success(questionService.listPublicApproved());
    }
}

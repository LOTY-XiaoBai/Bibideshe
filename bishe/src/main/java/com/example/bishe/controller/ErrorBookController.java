package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.ErrorBookItemView;
import com.example.bishe.dto.ErrorBookRemoveRequest;
import com.example.bishe.dto.ErrorBookSelection;
import com.example.bishe.service.ErrorBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 错题本相关接口(学生端)。
 */
@Tag(name = "错题本", description = "学生错题本查看、答案展开与移除")
@RestController
@RequestMapping("/api/error-book")
public class ErrorBookController {

    private final ErrorBookService errorBookService;

    public ErrorBookController(ErrorBookService errorBookService) {
        this.errorBookService = errorBookService;
    }

    /** 查看当前学生的错题本 */
    @Operation(summary = "查看学生的错题本")
    @GetMapping("/{studentId}")
    public Result<List<ErrorBookItemView>> list(@PathVariable Long studentId) {
        Long selfId = AuthContext.requireSelfStudentOrAdmin(studentId);
        return Result.success(errorBookService.listByStudent(selfId));
    }

    @Operation(summary = "展开当前学生收录的题目答案")
    @PostMapping("/answers")
    public Result<List<ErrorBookSelection.Answer>> answers(@RequestBody ErrorBookSelection request) {
        return Result.success(errorBookService.revealAnswers(AuthContext.requireStudentId(), request));
    }

    /** 从错题本移除一道题 */
    @Operation(summary = "从错题本移除一道题")
    @DeleteMapping("/remove")
    public Result<Void> remove(@RequestBody ErrorBookRemoveRequest request) {
        Long studentId = AuthContext.requireStudentId();
        errorBookService.remove(studentId, request.getQuestionId(), request.getQuestionType());
        return Result.success("已从错题本移除", null);
    }
}

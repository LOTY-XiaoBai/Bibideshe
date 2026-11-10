package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.ErrorBookSelection;
import com.example.bishe.service.ErrorBookRedoService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/error-book/redo")
public class ErrorBookRedoController {
    private final ErrorBookRedoService redo;

    public ErrorBookRedoController(ErrorBookRedoService redo) {
        this.redo = redo;
    }

    @PostMapping
    public Result<ErrorBookRedoService.Paper> start(@RequestBody ErrorBookSelection selection) {
        return Result.success(redo.start(AuthContext.requireStudentId(), selection));
    }

    @GetMapping("/{id}")
    public Result<ErrorBookRedoService.Paper> paper(@PathVariable String id) {
        return Result.success(redo.paper(AuthContext.requireStudentId(), id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> discard(@PathVariable String id) {
        redo.discard(AuthContext.requireStudentId(), id);
        return Result.success(null);
    }

    @PostMapping("/{id}/submit")
    public Result<ErrorBookRedoService.Review> submit(@PathVariable String id,
                                                     @RequestBody ErrorBookRedoService.Submission submission) {
        return Result.success(redo.submit(AuthContext.requireStudentId(), id, submission));
    }

    @GetMapping("/history")
    public Result<List<ErrorBookRedoService.HistoryItem>> history() {
        return Result.success(redo.history(AuthContext.requireStudentId()));
    }

    @GetMapping("/history/{id}")
    public Result<ErrorBookRedoService.Review> review(@PathVariable String id) {
        return Result.success(redo.review(AuthContext.requireStudentId(), id));
    }
}

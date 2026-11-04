package com.example.bishe.controller;

import com.example.bishe.common.*;
import com.example.bishe.service.StudyMistakeService;
import org.springframework.web.bind.annotation.*;

@RestController
public class StudyController {
    private final StudyMistakeService service;
    public StudyController(StudyMistakeService service){this.service=service;}
    @GetMapping("/api/study/{kind}/{id}/answers") public Result<?> answers(@PathVariable String kind,@PathVariable Long id){return Result.success(service.savedAnswers(AuthContext.requireStudentId(),kind,id));}
    @PostMapping("/api/error-book/add-batch") public Result<?> add(@RequestBody StudyMistakeService.Batch body){return Result.success(service.add(AuthContext.requireStudentId(),body));}
}

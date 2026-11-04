package com.example.bishe.controller;

import com.example.bishe.common.*;
import com.example.bishe.dto.QuizRequests.*;
import com.example.bishe.service.QuizService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {
    private final QuizService service;
    public QuizController(QuizService service) { this.service=service; }
    @GetMapping("/templates") public Result<?> templates() { return Result.success(service.templates(AuthContext.requireTeacherId())); }
    @GetMapping("/templates/{id}") public Result<?> template(@PathVariable Long id) { return Result.success(service.templateDetail(AuthContext.requireTeacherId(),id)); }
    @PostMapping("/templates") public Result<?> create(@RequestBody Template body) { return Result.success(service.saveTemplate(AuthContext.requireTeacherId(),null,body)); }
    @PutMapping("/templates/{id}") public Result<?> update(@PathVariable Long id,@RequestBody Template body) { return Result.success(service.saveTemplate(AuthContext.requireTeacherId(),id,body)); }
    @DeleteMapping("/templates/{id}") public Result<?> delete(@PathVariable Long id) { service.deleteTemplate(AuthContext.requireTeacherId(),id);return Result.success(null); }
    @PostMapping("/publish") public Result<?> publish(@RequestBody Publish body) { return Result.success(service.publish(AuthContext.requireTeacherId(),body)); }
    @GetMapping("/teacher") public Result<?> teacher(@RequestParam(required=false) Long courseId) { return Result.success(service.teacherRuns(AuthContext.requireTeacherId(),courseId)); }
    @GetMapping("/student") public Result<?> student() { return Result.success(service.studentRuns(AuthContext.requireStudentId())); }
    @GetMapping("/pending") public Result<?> pending() { return Result.success(service.pending(AuthContext.requireStudentId())); }
    @GetMapping("/{id}") public Result<?> detail(@PathVariable Long id) { return Result.success(service.detail(AuthContext.requireLogin(),id)); }
    @PostMapping("/{id}/draft") public Result<?> draft(@PathVariable Long id,@RequestBody Submit body) { service.draft(AuthContext.requireStudentId(),id,body);return Result.success(null); }
    @PostMapping("/{id}/submit") public Result<?> submit(@PathVariable Long id,@RequestBody Submit body) { return Result.success(service.submit(AuthContext.requireStudentId(),id,body)); }
    @PostMapping("/{id}/end") public Result<?> end(@PathVariable Long id) { service.end(AuthContext.requireTeacherId(),id);return Result.success(null); }
    @GetMapping("/{id}/scores") public Result<?> scores(@PathVariable Long id) { return Result.success(service.scores(AuthContext.requireLogin(),id)); }
    @GetMapping("/{id}/student/{studentId}") public Result<?> review(@PathVariable Long id,@PathVariable Long studentId) { return Result.success(service.teacherReview(AuthContext.requireTeacherId(),id,studentId)); }
    @PostMapping("/{id}/grade") public Result<?> grade(@PathVariable Long id,@RequestBody Grade body) { service.grade(AuthContext.requireTeacherId(),id,body);return Result.success(null); }
    @PostMapping("/{id}/mistakes") public Result<?> mistake(@PathVariable Long id,@RequestBody Mistake body) { service.addMistake(AuthContext.requireStudentId(),id,body);return Result.success(null); }
}

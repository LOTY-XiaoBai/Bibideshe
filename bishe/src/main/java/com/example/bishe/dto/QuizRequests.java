package com.example.bishe.dto;

import java.math.BigDecimal;
import java.util.List;

public final class QuizRequests {
    private QuizRequests() {}
    public record Item(Long questionId, Integer questionType, BigDecimal score) {}
    public record Template(String name, Integer timeLimitMinutes, Boolean hideAnswers, Boolean countScore,
                           Boolean showRanking, List<Item> questions) {}
    public record Publish(Long templateId, String sessionId) {}
    public record Answer(Long questionId, Integer questionType, String studentAnswer) {}
    public record Submit(List<Answer> answers) {}
    public record Grade(Long studentId, Long questionId, BigDecimal score, String annotation) {}
    public record Mistake(Long questionId, Integer questionType) {}
}

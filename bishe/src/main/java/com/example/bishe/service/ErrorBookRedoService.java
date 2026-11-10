package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.common.QuestionType;
import com.example.bishe.dto.AnswerItem;
import com.example.bishe.dto.ErrorBookSelection;
import com.example.bishe.dto.QuestionOptionView;
import com.example.bishe.dto.QuestionView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ErrorBookRedoService {
    public record Paper(String id, List<PaperItem> questions) {}

    public record PaperItem(PaperQuestion question) {}

    public record PaperQuestion(Long questionId, Integer questionType, String content,
                                List<QuestionOptionView> options, boolean multiple) {}

    public record Submission(List<AnswerItem> answers) {}

    public enum Status { CORRECT, INCORRECT, UNANSWERED, SELF_REVIEW }

    public record ReviewedAnswer(QuestionView question, String studentAnswer, Status status) {}

    public record Review(String id, int correct, int incorrect, int unanswered, List<ReviewedAnswer> answers) {}

    public record HistoryItem(String id, LocalDateTime submittedAt, int questionCount,
                              int correct, int incorrect, int unanswered) {}

    private record StoredPaper(String questions, String result) {}

    private final JdbcTemplate db;
    private final JsonMapper json;
    private final ErrorBookService errorBook;
    private final QuestionService questions;

    public ErrorBookRedoService(JdbcTemplate db, JsonMapper json, ErrorBookService errorBook, QuestionService questions) {
        this.db = db;
        this.json = json;
        this.errorBook = errorBook;
        this.questions = questions;
    }

    @Transactional
    public Paper start(Long studentId, ErrorBookSelection selection) {
        var selected = errorBook.ownedQuestions(studentId, selection).stream()
                .map(entry -> new QuestionService.QuestionRef(entry.getQuestionId(), entry.getQuestionType()))
                .toList();
        List<QuestionView> snapshot = questions.resolveQuestions(selected, false);
        if (snapshot.stream().anyMatch(QuestionView::isLost)) {
            throw new BusinessException("所选原题已删除，请返回错题本重新选择");
        }
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO error_book_redo(redo_id, student_id, questions_json) VALUES (?, ?, ?)",
                id, studentId, json.writeValueAsString(snapshot));
        return toPaper(id, snapshot);
    }

    public Paper paper(Long studentId, String id) {
        var rows = db.queryForList("""
                SELECT questions_json FROM error_book_redo
                WHERE student_id = ? AND redo_id = ? AND submitted_at IS NULL
                """, String.class, studentId, id);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "这次重做已结束或不存在");
        }
        return toPaper(id, List.of(json.readValue(rows.get(0), QuestionView[].class)));
    }

    public void discard(Long studentId, String id) {
        db.update("DELETE FROM error_book_redo WHERE student_id = ? AND redo_id = ? AND submitted_at IS NULL",
                studentId, id);
    }

    @Transactional
    public Review submit(Long studentId, String id, Submission submission) {
        var rows = db.query("""
                SELECT questions_json, result_json FROM error_book_redo
                WHERE student_id = ? AND redo_id = ? FOR UPDATE
                """, (row, index) -> new StoredPaper(row.getString("questions_json"), row.getString("result_json")),
                studentId, id);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "这次重做已结束或不存在");
        }
        var stored = rows.get(0);
        if (stored.result() != null) {
            return json.readValue(stored.result(), Review.class);
        }

        var snapshot = List.of(json.readValue(stored.questions(), QuestionView[].class));
        var submitted = submittedAnswers(snapshot, submission);
        var reviewed = snapshot.stream().map(question -> {
            String answer = submitted.getOrDefault(ref(question), "");
            return new ReviewedAnswer(question, answer, status(question, answer));
        }).toList();
        var counts = reviewed.stream().collect(Collectors.groupingBy(ReviewedAnswer::status, Collectors.summingInt(row -> 1)));
        var result = new Review(id, counts.getOrDefault(Status.CORRECT, 0), counts.getOrDefault(Status.INCORRECT, 0),
                counts.getOrDefault(Status.UNANSWERED, 0), reviewed);
        db.update("""
                UPDATE error_book_redo SET result_json = ?, submitted_at = CURRENT_TIMESTAMP(6)
                WHERE student_id = ? AND redo_id = ?
                """, json.writeValueAsString(result), studentId, id);
        return result;
    }

    public List<HistoryItem> history(Long studentId) {
        return db.query("""
                SELECT redo_id, submitted_at, JSON_LENGTH(questions_json) AS question_count,
                    result_json->>'$.correct' AS correct_count, result_json->>'$.incorrect' AS incorrect_count,
                    result_json->>'$.unanswered' AS unanswered_count
                FROM error_book_redo WHERE student_id = ? AND submitted_at IS NOT NULL
                ORDER BY submitted_at DESC, redo_id DESC
                """, (row, index) -> new HistoryItem(row.getString("redo_id"),
                row.getObject("submitted_at", LocalDateTime.class), row.getInt("question_count"),
                row.getInt("correct_count"), row.getInt("incorrect_count"), row.getInt("unanswered_count")), studentId);
    }

    public Review review(Long studentId, String id) {
        var rows = db.queryForList("""
                SELECT result_json FROM error_book_redo
                WHERE student_id = ? AND redo_id = ? AND submitted_at IS NOT NULL
                """, String.class, studentId, id);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "重做历史不存在");
        }
        return json.readValue(rows.get(0), Review.class);
    }

    private Map<ErrorBookSelection.Ref, String> submittedAnswers(List<QuestionView> snapshot, Submission submission) {
        if (submission == null || submission.answers() == null) {
            throw new BusinessException("作答数据无效");
        }
        var selected = snapshot.stream().map(this::ref).collect(Collectors.toSet());
        Map<ErrorBookSelection.Ref, String> submitted = new HashMap<>();
        for (var answer : submission.answers()) {
            if (answer == null) {
                throw new BusinessException("作答数据无效");
            }
            var ref = new ErrorBookSelection.Ref(answer.getQuestionId(), answer.getQuestionType());
            if (!selected.contains(ref) || submitted.putIfAbsent(ref, Objects.toString(answer.getStudentAnswer(), "")) != null) {
                throw new BusinessException("作答包含重复题目或未选中的题目");
            }
        }
        return submitted;
    }

    private ErrorBookSelection.Ref ref(QuestionView question) {
        return new ErrorBookSelection.Ref(question.getQuestionId(), question.getQuestionType());
    }

    private Status status(QuestionView question, String answer) {
        if (question.getQuestionType() == QuestionType.SOLVE) {
            return Status.SELF_REVIEW;
        }
        if (answer.isBlank()) {
            return Status.UNANSWERED;
        }
        return QuestionService.matchesObjectiveAnswer(question.getAnswer(), answer, question.getQuestionType())
                ? Status.CORRECT : Status.INCORRECT;
    }

    private Paper toPaper(String id, List<QuestionView> snapshot) {
        return new Paper(id, snapshot.stream().map(question -> {
            boolean multiple = question.getQuestionType() == QuestionType.CHOICE
                    && question.getAnswer() != null && question.getAnswer().replace('，', ',').contains(",");
            return new PaperItem(new PaperQuestion(question.getQuestionId(), question.getQuestionType(),
                    question.getContent(), question.getOptions(), multiple));
        }).toList());
    }
}

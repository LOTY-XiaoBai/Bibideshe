package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.common.QuestionType;
import com.example.bishe.dto.ErrorBookItemView;
import com.example.bishe.dto.ErrorBookSelection;
import com.example.bishe.dto.QuestionView;
import com.example.bishe.entity.ErrorBook;
import com.example.bishe.mapper.ErrorBookMapper;
import com.example.bishe.mapper.StudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 错题本列表、已收录题目的答案展开与移除。
 */
@Service
public class ErrorBookService {

    private final ErrorBookMapper errorBookMapper;
    private final StudentMapper studentMapper;
    private final QuestionService questionService;
    private final JsonMapper json;

    private record BookQuestion(QuestionView question, boolean archived) {}

    public ErrorBookService(ErrorBookMapper errorBookMapper,
                             StudentMapper studentMapper,
                             QuestionService questionService, JsonMapper json) {
        this.errorBookMapper = errorBookMapper;
        this.studentMapper = studentMapper;
        this.questionService = questionService;
        this.json = json;
    }

    /**
     * 查看错题本。已删除的题目优先展示复习快照，旧条目没有快照时展示占位。
     */
    @Transactional(readOnly = true)
    public List<ErrorBookItemView> listByStudent(Long studentId) {
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        if (studentMapper.findById(studentId) == null) {
            throw new BusinessException("学生不存在");
        }
        List<ErrorBookItemView> result = new ArrayList<>();
        for (ErrorBook errorBook : errorBookMapper.findByStudentId(studentId)) {
            ErrorBookItemView view = new ErrorBookItemView();
            view.setAddTime(errorBook.getAddTime());
            var entry = resolveBookQuestion(errorBook, true);
            view.setQuestion(entry.question());
            view.setArchived(entry.archived());
            result.add(view);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ErrorBookSelection.Answer> revealAnswers(Long studentId, ErrorBookSelection selection) {
        return ownedQuestions(studentId, selection).stream().map(entry -> {
            var question = resolveBookQuestion(entry, false).question();
            return new ErrorBookSelection.Answer(entry.getQuestionId(), entry.getQuestionType(), question.getAnswer());
        }).toList();
    }

    private BookQuestion resolveBookQuestion(ErrorBook entry, boolean hideAnswer) {
        var question = questionService.resolveQuestion(entry.getQuestionId(), entry.getQuestionType(), hideAnswer);
        if (question.isLost()) {
            String snapshot = errorBookMapper.findDeletedQuestion(entry.getQuestionId(), entry.getQuestionType());
            if (snapshot != null) {
                var archived = json.readValue(snapshot, QuestionView.class);
                archived.setLost(true);
                if (hideAnswer) archived.setAnswer(null);
                return new BookQuestion(archived, true);
            }
        }
        return new BookQuestion(question, false);
    }

    List<ErrorBook> ownedQuestions(Long studentId, ErrorBookSelection selection) {
        if (selection == null || selection.questions() == null || selection.questions().isEmpty()) {
            throw new BusinessException("请至少选择一道错题");
        }
        List<ErrorBook> entries = new ArrayList<>();
        for (var ref : new LinkedHashSet<>(selection.questions())) {
            if (ref == null || ref.questionId() == null || !QuestionType.isValid(ref.questionType())) {
                throw new BusinessException("题目参数无效");
            }
            var entry = errorBookMapper.find(studentId, ref.questionId(), ref.questionType());
            if (entry == null) {
                throw new BusinessException(403, "该题目不在您的错题本中");
            }
            entries.add(entry);
        }
        return entries;
    }

    /** 从错题本中移除一道题(学生认为已经掌握) */
    public void remove(Long studentId, Long questionId, Integer questionType) {
        if (studentId == null || questionId == null || questionType == null) {
            throw new BusinessException("参数不能为空");
        }
        if (errorBookMapper.find(studentId, questionId, questionType) == null) {
            throw new BusinessException("该题目不在您的错题本中");
        }
        errorBookMapper.delete(studentId, questionId, questionType);
    }
}

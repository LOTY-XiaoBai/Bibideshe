package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.dto.ErrorBookItemView;
import com.example.bishe.entity.ErrorBook;
import com.example.bishe.mapper.ErrorBookMapper;
import com.example.bishe.mapper.StudentMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 错题本服务(学生端: 2.9 错题加入错题本)。
 * 加入错题本的逻辑在 {@link ExamService}/{@link HomeworkService} 提交答案时触发,
 * 本服务负责查看与移除。
 */
@Service
public class ErrorBookService {

    private final ErrorBookMapper errorBookMapper;
    private final StudentMapper studentMapper;
    private final QuestionService questionService;

    public ErrorBookService(ErrorBookMapper errorBookMapper,
                             StudentMapper studentMapper,
                             QuestionService questionService) {
        this.errorBookMapper = errorBookMapper;
        this.studentMapper = studentMapper;
        this.questionService = questionService;
    }

    /**
     * 查看某学生的错题本。若题目已被管理员删除, 对应条目的题目内容会替换为
     * 占位文案"因为一些原因，该题目丢失了"。
     */
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
            view.setQuestion(questionService.resolveQuestion(errorBook.getQuestionId(), errorBook.getQuestionType(), false));
            result.add(view);
        }
        return result;
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

package com.example.bishe.service;

import com.example.bishe.common.*;
import com.example.bishe.dto.AnswerReviewItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 批量收藏作答中的题目；结果公布前禁止通过错题本获取参考答案。 */
@Service
public class StudyMistakeService {
    public record Ref(Long questionId,Integer questionType){}
    public record Batch(String source,Long sourceId,List<Ref> questions){}
    private final JdbcTemplate db;
    private final ExamService exams;
    private final HomeworkService homeworks;
    private final QuizService quizzes;
    private final QuestionService questions;
    public StudyMistakeService(JdbcTemplate db,ExamService exams,HomeworkService homeworks,QuizService quizzes,QuestionService questions){this.db=db;this.exams=exams;this.homeworks=homeworks;this.quizzes=quizzes;this.questions=questions;}
    private AuthUser student(Long id){return new AuthUser(id,id.toString(),UserRole.STUDENT);}
    public List<Map<String,Object>> savedAnswers(Long studentId,String kind,Long id){
        if("exam".equals(kind)){exams.getExamDetail(id,student(studentId));return db.queryForList("SELECT question_id AS questionId,question_type AS questionType,student_answer AS studentAnswer FROM exam_question_answer WHERE exam_id=? AND student_id=?",id,studentId);}
        if("homework".equals(kind)){homeworks.getHomeworkDetail(id,student(studentId));return db.queryForList("SELECT question_id AS questionId,question_type AS questionType,student_answer AS studentAnswer FROM homework_question_answer WHERE homework_id=? AND student_id=?",id,studentId);}
        throw new BusinessException("不支持的作答类型");
    }
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String,Integer> add(Long studentId,Batch request){
        if(request==null || request.sourceId()==null || request.source()==null || request.questions()==null || request.questions().isEmpty() || request.questions().size()>1000)throw new BusinessException("请选择需要加入错题本的题目");
        var allowed=new HashSet<String>();boolean answered=false;
        switch(request.source().toUpperCase(Locale.ROOT)){
            case "EXAM","HOMEWORK" -> {
                List<AnswerReviewItem> rows="EXAM".equalsIgnoreCase(request.source())?exams.reviewExam(request.sourceId(),studentId).getAnswers():homeworks.reviewHomework(request.sourceId(),studentId).getAnswers();
                for(var row:rows){var q=row.getQuestion();if(q!=null)allowed.add(q.getQuestionType()+":"+q.getQuestionId());if(row.getStudentAnswer()!=null && !row.getStudentAnswer().isBlank())answered=true;}
            }
            case "QUIZ" -> {
                var detail=quizzes.detail(student(studentId),request.sourceId());
                if(!Boolean.TRUE.equals(detail.get("answersVisible")))throw new BusinessException(403,"答案公布后才能加入错题本");
                for(var row:(List<Map<String,Object>>)detail.get("answers")){
                    allowed.add(row.get("questionType")+":"+row.get("questionId"));
                    if(row.get("studentAnswer")!=null && !row.get("studentAnswer").toString().isBlank())answered=true;
                }
            }
            default -> throw new BusinessException("不支持的作答类型");
        }
        if(!answered)throw new BusinessException("有作答记录后才能加入错题本");
        var unique=new LinkedHashSet<Ref>();
        for(var ref:request.questions()){
            if(ref==null || ref.questionId()==null || !QuestionType.isValid(ref.questionType()) || !allowed.contains(ref.questionType()+":"+ref.questionId()))throw new BusinessException(403,"题目不属于您的本次作答");
            if(!questions.existsQuestion(ref.questionId(),ref.questionType()))throw new BusinessException("所选原题已被删除，请取消选择该题");
            unique.add(ref);
        }
        int added=0;for(var ref:unique)added+=db.update("INSERT IGNORE INTO error_book(student_id,question_id,question_type,add_time) VALUES(?,?,?,CURRENT_TIMESTAMP)",studentId,ref.questionId(),ref.questionType());
        return Map.of("addedCount",added,"selectedCount",unique.size());
    }
}

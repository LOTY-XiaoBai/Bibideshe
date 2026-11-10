package com.example.bishe.service;

import com.example.bishe.common.*;
import com.example.bishe.dto.QuizRequests.*;
import com.example.bishe.dto.QuestionView;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/** 预备小测、课堂发布、答卷和复盘；答案可见性由服务端控制。 */
@Service
public class QuizService {
    private static final String RUN_COLUMNS = "q.id,q.template_id AS templateId,q.course_id AS courseId,q.teacher_id AS teacherId,q.session_id AS sessionId,q.name,q.mode,q.hide_answers AS hideAnswers,q.count_score AS countScore,q.show_ranking AS showRanking,q.max_score AS maxScore,q.started_at AS startedAt,q.deadline,q.ended_at AS endedAt";
    private static final String TEMPLATE_COLUMNS = "t.id,t.teacher_id AS teacherId,t.name,t.time_limit_minutes AS timeLimitMinutes,t.hide_answers AS hideAnswers,t.count_score AS countScore,t.show_ranking AS showRanking,t.updated_at AS updatedAt,(SELECT COUNT(*) FROM quiz_template_question i WHERE i.template_id=t.id) AS questionCount,(SELECT COALESCE(SUM(i.score),0) FROM quiz_template_question i WHERE i.template_id=t.id) AS maxScore";
    private final JdbcTemplate db;
    private final QuestionService questions;
    private final CourseService courses;
    private final ClassroomService classroom;
    private final tools.jackson.databind.json.JsonMapper json = new tools.jackson.databind.json.JsonMapper();

    public QuizService(JdbcTemplate db, QuestionService questions, CourseService courses, ClassroomService classroom) {
        this.db=db; this.questions=questions; this.courses=courses; this.classroom=classroom;
    }
    private long number(Object v) { return ((Number)v).longValue(); }
    private boolean flag(Object v) { return Boolean.TRUE.equals(v) || v instanceof Number n && n.intValue()==1; }
    private BigDecimal decimal(Object v) { return v==null ? BigDecimal.ZERO : new BigDecimal(v.toString()); }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new BusinessException("题目快照格式错误"); } }
    @SuppressWarnings("unchecked")
    private Map<String,Object> snapshot(Object value) { try { return new LinkedHashMap<>(json.readValue(value.toString(),Map.class)); } catch(Exception e) { throw new BusinessException("题目快照读取失败"); } }
    private long insert(String sql,Object... values) {
        var key=new GeneratedKeyHolder();
        db.update(connection->{var s=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);return s;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private Map<String,Object> template(Long teacherId,Long id,boolean lock) {
        var rows=db.queryForList("SELECT "+TEMPLATE_COLUMNS+" FROM quiz_template t WHERE t.id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty() || number(rows.get(0).get("teacherId"))!=teacherId)throw new BusinessException(403,"无权访问该预备小测");
        return rows.get(0);
    }
    public List<Map<String,Object>> templates(Long teacherId) {
        return db.queryForList("SELECT "+TEMPLATE_COLUMNS+" FROM quiz_template t WHERE t.teacher_id=? ORDER BY t.updated_at DESC,t.id DESC",teacherId);
    }
    public Map<String,Object> templateDetail(Long teacherId,Long id) {
        var result=template(teacherId,id,false);
        result.put("questions",items("quiz_template_question","template_id",id,true));
        return result;
    }
    @Transactional
    public Map<String,Object> saveTemplate(Long teacherId,Long id,Template request) {
        if(request==null || request.name()==null || request.name().trim().isEmpty() || request.name().trim().length()>100)throw new BusinessException("小测名称应为1至100字");
        if(request.timeLimitMinutes()!=null && (request.timeLimitMinutes()<1 || request.timeLimitMinutes()>180))throw new BusinessException("固定作答时长应为1至180分钟");
        if(request.questions()==null || request.questions().isEmpty() || request.questions().size()>20)throw new BusinessException("每份小测最少1题，最多20题");
        var snapshots=new ArrayList<QuestionView>();
        var seen=new HashSet<String>();
        for(Item item:request.questions()) {
            if(item==null || item.questionId()==null || !QuestionType.isValid(item.questionType()) || item.score()==null || item.score().scale()>2 || item.score().compareTo(BigDecimal.ZERO)<=0 || item.score().compareTo(new BigDecimal("100"))>0)throw new BusinessException("题目及分值无效，每题应为0.01至100分");
            if(!seen.add(item.questionType()+":"+item.questionId()))throw new BusinessException("同一小测不能重复添加同一道题");
            var q=questions.resolveQuestion(item.questionId(),item.questionType(),false);
            if(q.isLost() || !(Objects.equals(q.getPublisherId(),teacherId) || Integer.valueOf(1).equals(q.getIsPublic()) && Integer.valueOf(1).equals(q.getAuditStatus())))throw new BusinessException(403,"只能使用自己的题目或已审核的公共题目");
            snapshots.add(q);
        }
        if(id==null)id=insert("INSERT INTO quiz_template(teacher_id,name,time_limit_minutes,hide_answers,count_score,show_ranking) VALUES(?,?,?,?,?,?)",teacherId,request.name().trim(),request.timeLimitMinutes(),Boolean.TRUE.equals(request.hideAnswers()),Boolean.TRUE.equals(request.countScore()),Boolean.TRUE.equals(request.showRanking()));
        else {
            template(teacherId,id,true);
            db.update("UPDATE quiz_template SET name=?,time_limit_minutes=?,hide_answers=?,count_score=?,show_ranking=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",request.name().trim(),request.timeLimitMinutes(),Boolean.TRUE.equals(request.hideAnswers()),Boolean.TRUE.equals(request.countScore()),Boolean.TRUE.equals(request.showRanking()),id);
            db.update("DELETE FROM quiz_template_question WHERE template_id=?",id);
        }
        for(int i=0;i<request.questions().size();i++) {
            Item item=request.questions().get(i);
            db.update("INSERT INTO quiz_template_question(template_id,position,question_id,question_type,score,snapshot_json) VALUES(?,?,?,?,?,?)",id,i+1,item.questionId(),item.questionType(),item.score(),encode(snapshots.get(i)));
        }
        return templateDetail(teacherId,id);
    }
    @Transactional
    public void deleteTemplate(Long teacherId,Long id) { template(teacherId,id,true);db.update("DELETE FROM quiz_template WHERE id=?",id); }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> publish(Long teacherId,Publish request) {
        if(request==null || request.sessionId()==null || request.templateId()==null)throw new BusinessException("请选择当前课堂和预备小测");
        var user=new AuthUser(teacherId,teacherId.toString(),UserRole.TEACHER);
        var session=classroom.requireSession(user,request.sessionId());
        long courseId=number(session.get("courseId"));
        // 和课堂开始/结束共用课程锁，防止结束课堂时仍发布成功。
        db.queryForList("SELECT course_id FROM course WHERE course_id=? FOR UPDATE",courseId);
        session=classroom.requireSession(user,request.sessionId());
        classroom.owned(teacherId,courseId);
        var t=template(teacherId,request.templateId(),true);
        var open=db.queryForList("SELECT id FROM classroom_quiz WHERE session_id=? AND ended_at IS NULL",request.sessionId());
        for(var row:open)touch(number(row.get("id")));
        if(db.queryForObject("SELECT COUNT(*) FROM classroom_quiz WHERE session_id=? AND ended_at IS NULL",Integer.class,request.sessionId())>0)throw new BusinessException("请先结束当前小测，再发布下一份");
        var members=db.queryForList("SELECT p.student_id FROM course_personnel p JOIN student s ON s.student_id=p.student_id WHERE p.course_id=? AND s.is_deleted=0 AND s.is_banned=0",courseId);
        if(members.isEmpty())throw new BusinessException("当前课程暂无可参加小测的学生");
        int count=((Number)t.get("questionCount")).intValue();
        if(count<1 || count>20)throw new BusinessException("小测题目数量应为1至20题");
        LocalDateTime deadline=t.get("timeLimitMinutes")==null?null:LocalDateTime.now().plusMinutes(number(t.get("timeLimitMinutes")));
        long id=insert("INSERT INTO classroom_quiz(template_id,course_id,teacher_id,session_id,name,mode,hide_answers,count_score,show_ranking,max_score,deadline) VALUES(?,?,?,?,?,?,?,?,?,?,?)",request.templateId(),courseId,teacherId,request.sessionId(),t.get("name"),session.get("mode"),flag(t.get("hideAnswers")),flag(t.get("countScore")),flag(t.get("showRanking")),t.get("maxScore"),deadline);
        db.update("INSERT INTO classroom_quiz_question(quiz_id,position,question_id,question_type,score,snapshot_json) SELECT ?,position,question_id,question_type,score,snapshot_json FROM quiz_template_question WHERE template_id=?",id,request.templateId());
        for(var member:members)db.update("INSERT INTO classroom_quiz_student(quiz_id,student_id) VALUES(?,?)",id,member.get("student_id"));
        var run=run(id,false);
        classroom.broadcast(courseId,"QUIZ_PUBLISHED",notice(run));
        return run;
    }
    private Map<String,Object> run(Long id,boolean lock) {
        var rows=db.queryForList("SELECT "+RUN_COLUMNS+" FROM classroom_quiz q WHERE q.id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty())throw new BusinessException(404,"小测不存在");
        return rows.get(0);
    }
    private Map<String,Object> notice(Map<String,Object> run) {
        var data=new LinkedHashMap<String,Object>();
        for(String key:List.of("id","courseId","sessionId","name","mode","deadline"))data.put(key,run.get(key));
        return data;
    }
    private void accessible(AuthUser user,Map<String,Object> run) {
        classroom.accessible(user,number(run.get("courseId")));
        if(UserRole.TEACHER.equals(user.getRole())) {
            if(number(run.get("teacherId"))!=user.getUserId())throw new BusinessException(403,"无权操作该小测");
        } else if(!UserRole.STUDENT.equals(user.getRole()) || db.queryForObject("SELECT COUNT(*) FROM classroom_quiz_student WHERE quiz_id=? AND student_id=?",Integer.class,run.get("id"),user.getUserId())==0)throw new BusinessException(403,"您不在本次小测名单中");
    }
    private boolean ended(Map<String,Object> run) { return run.get("endedAt")!=null; }
    private Map<String,Object> touch(Long id) {
        var q=run(id,true);
        if(!ended(q)) {
            Object value=q.get("deadline");
            LocalDateTime deadline=value instanceof Timestamp t?t.toLocalDateTime():value instanceof LocalDateTime t?t:null;
            boolean timeout=deadline!=null && !deadline.isAfter(LocalDateTime.now());
            var active=classroom.active(number(q.get("courseId")));
            if(timeout || active==null || !Objects.equals(active.get("sessionId"),q.get("sessionId"))) { closeLocked(q);q=run(id,false); }
        }
        return q;
    }
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> items(String table,String fk,Long id,boolean showAnswer) {
        var rows=db.queryForList("SELECT question_id AS questionId,question_type AS questionType,position,score,snapshot_json FROM "+table+" WHERE "+fk+"=? ORDER BY position",id);
        var result=new ArrayList<Map<String,Object>>();
        for(var row:rows) {
            var q=snapshot(row.get("snapshot_json"));
            q.put("multiple",number(row.get("questionType"))==1 && q.get("answer").toString().replace("，",",").contains(","));
            if(!showAnswer) { q.put("answer",null); q.remove("publisherId");q.remove("auditStatus"); }
            var item=new LinkedHashMap<String,Object>(); item.put("question",q);item.put("score",row.get("score"));item.put("position",row.get("position"));
            result.add(item);
        }
        return result;
    }
    public List<Map<String,Object>> teacherRuns(Long teacherId,Long courseId) {
        if(courseId!=null)classroom.owned(teacherId,courseId);
        return db.queryForList("SELECT "+RUN_COLUMNS+",c.course_name AS courseName,(SELECT COUNT(*) FROM classroom_quiz_student s WHERE s.quiz_id=q.id AND s.status='SUBMITTED') AS submittedCount,(SELECT COUNT(*) FROM classroom_quiz_student s WHERE s.quiz_id=q.id) AS studentCount FROM classroom_quiz q JOIN course c ON c.course_id=q.course_id WHERE q.teacher_id=?"+(courseId==null?"":" AND q.course_id=?")+" ORDER BY q.id DESC",courseId==null?new Object[]{teacherId}:new Object[]{teacherId,courseId});
    }
    public List<Map<String,Object>> studentRuns(Long studentId) {
        var rows=db.queryForList("SELECT "+RUN_COLUMNS+",c.course_name AS courseName,s.status,s.score,s.graded FROM classroom_quiz q JOIN classroom_quiz_student s ON s.quiz_id=q.id JOIN course c ON c.course_id=q.course_id JOIN course_personnel p ON p.course_id=q.course_id AND p.student_id=s.student_id WHERE s.student_id=? ORDER BY q.id DESC",studentId);
        for(var row:rows)if(flag(row.get("hideAnswers")) && !ended(row))row.put("score",null);
        return rows;
    }
    public List<Map<String,Object>> pending(Long studentId) {
        return db.queryForList("SELECT "+RUN_COLUMNS+",c.course_name AS courseName FROM classroom_quiz q JOIN classroom_quiz_student s ON s.quiz_id=q.id JOIN course c ON c.course_id=q.course_id JOIN course_personnel p ON p.course_id=q.course_id AND p.student_id=s.student_id WHERE s.student_id=? AND s.status IN ('PENDING','DRAFT') AND q.ended_at IS NULL AND (q.deadline IS NULL OR q.deadline>CURRENT_TIMESTAMP) AND EXISTS(SELECT 1 FROM course_session cs WHERE cs.session_id=q.session_id AND cs.ended_at IS NULL AND cs.heartbeat_at>DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 60 SECOND)) ORDER BY q.id",studentId);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> detail(AuthUser user,Long id) {
        var q=run(id,false);accessible(user,q);q=touch(id);
        boolean teacher=UserRole.TEACHER.equals(user.getRole());
        Map<String,Object> student=teacher?null:db.queryForMap("SELECT status,score,graded,submitted_at AS submittedAt FROM classroom_quiz_student WHERE quiz_id=? AND student_id=?",id,user.getUserId());
        boolean submitted=teacher || Set.of("SUBMITTED","ABSENT").contains(student.get("status"));
        boolean answersVisible=teacher || submitted && (!flag(q.get("hideAnswers")) || ended(q));
        q.put("questions",items("classroom_quiz_question","quiz_id",id,answersVisible));
        q.put("answersVisible",answersVisible);
        if(!teacher) {
            if(!answersVisible)student.put("score",null);
            q.put("student",student);
            var saved=db.queryForList("SELECT question_id AS questionId,question_type AS questionType,student_answer AS studentAnswer,score,annotation FROM classroom_quiz_answer WHERE quiz_id=? AND student_id=? ORDER BY question_type,question_id",id,user.getUserId());
            var review=new ArrayList<Map<String,Object>>();
            for(var a:saved) {
                if(!answersVisible){a.put("score",null);a.put("annotation","");}
                var item=itemsForAnswer(q,a);a.put("question",item.get("question"));a.put("fullScore",item.get("score"));review.add(a);
            }
            q.put("answers",review);
        }
        return q;
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> itemsForAnswer(Map<String,Object> run,Map<String,Object> answer) {
        return ((List<Map<String,Object>>)run.get("questions")).stream().filter(i->{var q=(Map<String,Object>)i.get("question");return number(q.get("questionId"))==number(answer.get("questionId")) && number(q.get("questionType"))==number(answer.get("questionType"));}).findFirst().orElseThrow();
    }
    private AuthUser student(Long id) { return new AuthUser(id,id.toString(),UserRole.STUDENT); }
    private Map<String,Object> studentRecord(Long id,Long studentId) { return db.queryForMap("SELECT status FROM classroom_quiz_student WHERE quiz_id=? AND student_id=?",id,studentId); }
    private void storeAnswers(Long id,Long studentId,Submit request) {
        if(request==null || request.answers()==null || request.answers().size()>20)throw new BusinessException("作答格式错误");
        var valid=db.queryForList("SELECT question_id,question_type FROM classroom_quiz_question WHERE quiz_id=?",id);
        var answers=new HashMap<String,String>();
        for(Answer a:request.answers()) {
            if(a==null || a.questionId()==null || a.questionType()==null)throw new BusinessException("作答题目无效");
            String key=a.questionType()+":"+a.questionId();
            if(answers.containsKey(key) || valid.stream().noneMatch(v->number(v.get("question_id"))==a.questionId() && number(v.get("question_type"))==a.questionType()))throw new BusinessException("存在重复或不属于本次小测的题目");
            String value=a.studentAnswer()==null?"":a.studentAnswer().trim();
            if(value.length()>10000)throw new BusinessException("单题作答不能超过10000字");
            answers.put(key,value);
        }
        for(var v:valid) {
            String value=answers.getOrDefault(v.get("question_type")+":"+v.get("question_id"),"");
            db.update("INSERT INTO classroom_quiz_answer(quiz_id,student_id,question_id,question_type,student_answer) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE student_answer=VALUES(student_answer)",id,studentId,v.get("question_id"),v.get("question_type"),value);
        }
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void draft(Long studentId,Long id,Submit request) {
        var q=run(id,false);accessible(student(studentId),q);q=touch(id);
        if(ended(q))throw new BusinessException(409,"小测已结束，保存的作答已自动交卷");
        String status=studentRecord(id,studentId).get("status").toString();
        if("SUBMITTED".equals(status))throw new BusinessException(409,"已提交的小测不能修改");
        storeAnswers(id,studentId,request);
        db.update("UPDATE classroom_quiz_student SET status='DRAFT' WHERE quiz_id=? AND student_id=?",id,studentId);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> submit(Long studentId,Long id,Submit request) {
        var q=run(id,false);accessible(student(studentId),q);q=touch(id);
        if(ended(q) || "SUBMITTED".equals(studentRecord(id,studentId).get("status")))return detail(student(studentId),id);
        storeAnswers(id,studentId,request);gradeStored(q,studentId);
        return detail(student(studentId),id);
    }
    private void gradeStored(Map<String,Object> q,Long studentId) {
        long id=number(q.get("id"));
        var rows=db.queryForList("SELECT i.*,a.student_answer FROM classroom_quiz_question i JOIN classroom_quiz_answer a ON a.quiz_id=i.quiz_id AND a.question_id=i.question_id AND a.question_type=i.question_type WHERE i.quiz_id=? AND a.student_id=?",id,studentId);
        for(var row:rows) {
            int type=((Number)row.get("question_type")).intValue();
            String value=Objects.toString(row.get("student_answer"),"");
            BigDecimal score=null;String annotation="待教师批改";
            if(value.isBlank()){score=BigDecimal.ZERO;annotation="未作答";}
            else if(type!=3) {
                String correct=snapshot(row.get("snapshot_json")).get("answer").toString();
                score=QuestionService.matchesObjectiveAnswer(correct,value,type)?decimal(row.get("score")):BigDecimal.ZERO;annotation="";
            }
            db.update("UPDATE classroom_quiz_answer SET score=?,annotation=? WHERE quiz_id=? AND student_id=? AND question_id=? AND question_type=?",score,annotation,id,studentId,row.get("question_id"),type);
        }
        db.update("UPDATE classroom_quiz_student SET status='SUBMITTED',submitted_at=CURRENT_TIMESTAMP WHERE quiz_id=? AND student_id=?",id,studentId);
        syncStudent(id,studentId);
    }
    private void syncStudent(Long id,Long studentId) {
        db.update("UPDATE classroom_quiz_student s SET score=(SELECT COALESCE(SUM(a.score),0) FROM classroom_quiz_answer a WHERE a.quiz_id=s.quiz_id AND a.student_id=s.student_id),graded=NOT EXISTS(SELECT 1 FROM classroom_quiz_answer a WHERE a.quiz_id=s.quiz_id AND a.student_id=s.student_id AND a.score IS NULL) WHERE s.quiz_id=? AND s.student_id=?",id,studentId);
    }
    private void refreshCourse(Long courseId) {
        for(Long id:db.queryForList("SELECT student_id FROM course_personnel WHERE course_id=?",Long.class,courseId)) {
            courses.recalculateAndUpdateStudentScore(courseId,id);courses.updateComprehensiveScoreForStudent(id);
        }
    }
    private void closeLocked(Map<String,Object> q) {
        if(ended(q))return;
        long id=number(q.get("id"));
        for(var s:db.queryForList("SELECT student_id,status FROM classroom_quiz_student WHERE quiz_id=? AND status IN ('PENDING','DRAFT') ORDER BY student_id",id)) {
            long studentId=number(s.get("student_id"));
            if("DRAFT".equals(s.get("status")))gradeStored(q,studentId);
            else {
                storeAnswers(id,studentId,new Submit(List.of()));
                db.update("UPDATE classroom_quiz_answer SET score=0,annotation='未作答' WHERE quiz_id=? AND student_id=?",id,studentId);
                db.update("UPDATE classroom_quiz_student SET status='ABSENT',graded=1 WHERE quiz_id=? AND student_id=?",id,studentId);
            }
        }
        db.update("UPDATE classroom_quiz SET ended_at=CURRENT_TIMESTAMP WHERE id=? AND ended_at IS NULL",id);
        q.put("endedAt",Timestamp.valueOf(LocalDateTime.now()));
        if(flag(q.get("countScore")))refreshCourse(number(q.get("courseId")));
        classroom.broadcast(number(q.get("courseId")),"QUIZ_ENDED",notice(q));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void end(Long teacherId,Long id) {
        var q=run(id,true);
        if(number(q.get("teacherId"))!=teacherId)throw new BusinessException(403,"无权结束该小测");
        closeLocked(q);
    }
    @EventListener
    @Transactional
    public void onClassEnded(ClassroomEndedEvent event) {
        for(Long id:db.queryForList("SELECT id FROM classroom_quiz WHERE course_id=? AND ended_at IS NULL ORDER BY id",Long.class,event.courseId()))closeLocked(run(id,true));
    }
    @Scheduled(fixedDelay=5000,initialDelay=5000)
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void expire() {
        for(Long id:db.queryForList("SELECT q.id FROM classroom_quiz q WHERE q.ended_at IS NULL AND ((q.deadline IS NOT NULL AND q.deadline<=CURRENT_TIMESTAMP) OR NOT EXISTS(SELECT 1 FROM course_session s WHERE s.session_id=q.session_id AND s.ended_at IS NULL AND s.heartbeat_at>DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 60 SECOND))) ORDER BY q.id",Long.class))touch(id);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public List<Map<String,Object>> scores(AuthUser user,Long id) {
        var q=run(id,false);accessible(user,q);q=touch(id);
        boolean teacher=UserRole.TEACHER.equals(user.getRole());
        if(!teacher) {
            String status=studentRecord(id,user.getUserId()).get("status").toString();
            if(!flag(q.get("showRanking")) || !ended(q) && flag(q.get("hideAnswers")) || !"SUBMITTED".equals(status) && !ended(q))throw new BusinessException(403,"本次小测暂不开放排名");
        }
        String filter=teacher?"":" AND s.status='SUBMITTED' AND s.graded=1";
        var rows=db.queryForList("SELECT s.student_id AS studentId,u.nickname,u.avatar,s.status,s.score,s.graded,s.submitted_at AS submittedAt FROM classroom_quiz_student s JOIN student u ON u.student_id=s.student_id WHERE s.quiz_id=?"+filter+" ORDER BY s.graded DESC,s.score DESC,s.submitted_at,s.student_id",id);
        int index=0,rank=0;BigDecimal previous=null;
        for(var row:rows) {
            if("SUBMITTED".equals(row.get("status")) && flag(row.get("graded"))) { index++;var score=decimal(row.get("score"));if(previous==null || score.compareTo(previous)!=0)rank=index;row.put("rank",rank);previous=score; }
            else row.put("rank",null);
        }
        return rows;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> teacherReview(Long teacherId,Long id,Long studentId) {
        var user=new AuthUser(teacherId,teacherId.toString(),UserRole.TEACHER);
        var result=detail(user,id);
        var answers=db.queryForList("SELECT question_id AS questionId,question_type AS questionType,student_answer AS studentAnswer,score,annotation FROM classroom_quiz_answer WHERE quiz_id=? AND student_id=?",id,studentId);
        if(answers.isEmpty())throw new BusinessException("该学生尚未提交小测");
        String status=studentRecord(id,studentId).get("status").toString();
        if(!Set.of("SUBMITTED","ABSENT").contains(status))throw new BusinessException("该学生尚未提交小测");
        for(var a:answers){var item=itemsForAnswer(result,a);a.put("question",item.get("question"));a.put("fullScore",item.get("score"));}
        result.put("answers",answers);return result;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void grade(Long teacherId,Long id,Grade request) {
        var q=run(id,true);
        if(number(q.get("teacherId"))!=teacherId)throw new BusinessException(403,"无权批阅该小测");
        if(request==null || request.studentId()==null || request.questionId()==null || request.score()==null || request.score().scale()>2 || request.annotation()!=null && request.annotation().length()>500)throw new BusinessException("批阅参数无效");
        var rows=db.queryForList("SELECT i.score AS fullScore,s.status FROM classroom_quiz_question i JOIN classroom_quiz_student s ON s.quiz_id=i.quiz_id WHERE i.quiz_id=? AND i.question_id=? AND i.question_type=3 AND s.student_id=?",id,request.questionId(),request.studentId());
        if(rows.isEmpty() || !"SUBMITTED".equals(rows.get(0).get("status")))throw new BusinessException("该解答题尚未提交");
        if(request.score().compareTo(BigDecimal.ZERO)<0 || request.score().compareTo(decimal(rows.get(0).get("fullScore")))>0)throw new BusinessException("评分不能低于0或超过该题满分");
        db.update("UPDATE classroom_quiz_answer SET score=?,annotation=? WHERE quiz_id=? AND student_id=? AND question_id=? AND question_type=3",request.score(),request.annotation()==null?"已批阅":request.annotation(),id,request.studentId(),request.questionId());
        syncStudent(id,request.studentId());
        if(ended(q) && flag(q.get("countScore")))refreshCourse(number(q.get("courseId")));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void addMistake(Long studentId,Long id,Mistake request) {
        if(request==null || request.questionId()==null || request.questionType()==null)throw new BusinessException("请选择题目");
        var detail=detail(student(studentId),id);
        if(!Boolean.TRUE.equals(detail.get("answersVisible")))throw new BusinessException(403,"小测结束公布答案后才能加入错题本");
        var rows=db.queryForList("SELECT a.score,i.score AS fullScore FROM classroom_quiz_answer a JOIN classroom_quiz_question i ON i.quiz_id=a.quiz_id AND i.question_id=a.question_id AND i.question_type=a.question_type WHERE a.quiz_id=? AND a.student_id=? AND a.question_id=? AND a.question_type=?",id,studentId,request.questionId(),request.questionType());
        if(rows.isEmpty() || rows.get(0).get("score")==null || decimal(rows.get(0).get("score")).compareTo(decimal(rows.get(0).get("fullScore")))>=0)throw new BusinessException("只能将已批阅的错题加入错题本");
        if(!questions.existsQuestion(request.questionId(),request.questionType()))throw new BusinessException("原题已从题库删除，小测内仍可查看快照");
        db.update("INSERT IGNORE INTO error_book(student_id,question_id,question_type,add_time) VALUES(?,?,?,CURRENT_TIMESTAMP)",studentId,request.questionId(),request.questionType());
    }
}

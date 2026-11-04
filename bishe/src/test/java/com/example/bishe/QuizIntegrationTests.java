package com.example.bishe;

import com.example.bishe.common.*;
import com.example.bishe.dto.*;
import com.example.bishe.dto.QuizRequests.*;
import com.example.bishe.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="app.token.secret=test-token-secret-with-at-least-32-characters")
@EnabledIfEnvironmentVariable(named="RUN_DB_INTEGRATION",matches="true")
@Transactional
class QuizIntegrationTests {
    @Autowired QuizService quizzes;
    @Autowired CourseService courses;
    @Autowired ClassroomService classroom;
    @Autowired QuestionService questions;
    @Autowired JdbcTemplate db;
    @Autowired com.example.bishe.controller.QuestionController questionController;
    long teacher,a,b,course,choice,judge,solve;
    String session;
    long id(){return 6_000_000_000_000L+ThreadLocalRandom.current().nextLong(1_000_000_000_000L);}
    long student(){long id=id();db.update("INSERT INTO student(student_id,nickname,password) VALUES(?,'小测测试学生','disabled-test-account')",id);return id;}
    AuthUser studentAuth(long id){return new AuthUser(id,""+id,UserRole.STUDENT);}
    AuthUser teacherAuth(){return new AuthUser(teacher,""+teacher,UserRole.TEACHER);}
    long question(int type,String answer){var r=new QuestionCreateRequest();r.setPublisherId(teacher);r.setQuestionType(type);r.setContent("小测测试 "+UUID.randomUUID());r.setAnswer(answer);r.setOptions(List.of("一","二","三","四"));return questions.createQuestion(r).getQuestionId();}
    @BeforeEach void setup(){
        teacher=id();db.update("INSERT INTO teacher(teacher_id,name,password) VALUES(?,'小测测试教师','disabled-test-account')",teacher);a=student();b=student();
        var r=new CourseCreateRequest();r.setTeacherId(teacher);r.setCourseName("课堂小测回归测试");course=courses.createCourse(r).getCourseId();courses.joinCourse(a,course);courses.joinCourse(b,course);
        session=classroom.start(teacher,course,"LOCAL").get("sessionId").toString();choice=question(1,"A");judge=question(2,"正确");solve=question(3,"4");
    }
    @AfterEach void clearAuth(){AuthContext.clear();}
    Item item(long id,int type){return new Item(id,type,new BigDecimal("5"));}
    Template config(boolean hide,boolean count,boolean ranking,Item... items){return new Template("单元巩固小测",10,hide,count,ranking,List.of(items));}
    long template(Template c){return ((Number)quizzes.saveTemplate(teacher,null,c).get("id")).longValue();}
    long publish(Template c){return ((Number)quizzes.publish(teacher,new Publish(template(c),session)).get("id")).longValue();}
    Submit answers(Answer... a){return new Submit(List.of(a));}
    @SuppressWarnings("unchecked") List<Map<String,Object>> list(Map<String,Object> map,String key){return (List<Map<String,Object>>)map.get(key);}
    @SuppressWarnings("unchecked") Map<String,Object> map(Map<String,Object> map,String key){return (Map<String,Object>)map.get(key);}
    BigDecimal score(){return db.queryForObject("SELECT student_score FROM course_personnel WHERE course_id=? AND student_id=?",BigDecimal.class,course,a);}

    @Test void templateRequiresNameOneToTwentyUniqueAccessibleQuestions(){
        assertThatThrownBy(()->quizzes.saveTemplate(teacher,null,config(false,false,false))).hasMessageContaining("最少1题");
        var many=new ArrayList<Item>();for(int i=0;i<21;i++)many.add(item(choice,1));
        assertThatThrownBy(()->quizzes.saveTemplate(teacher,null,new Template("过多题目",null,false,false,false,many))).hasMessageContaining("最多20题");
        assertThatThrownBy(()->quizzes.saveTemplate(teacher,null,config(false,false,false,item(choice,1),item(choice,1)))).hasMessageContaining("重复");
        assertThatThrownBy(()->quizzes.saveTemplate(teacher,null,new Template("",null,false,false,false,List.of(item(choice,1))))).hasMessageContaining("名称");
        assertThatThrownBy(()->quizzes.saveTemplate(teacher,null,new Template("小测",0,false,false,false,List.of(item(choice,1))))).hasMessageContaining("作答时长");
        long stranger=id();db.update("INSERT INTO teacher(teacher_id,name,password) VALUES(?,'其他教师','disabled')",stranger);
        assertThatThrownBy(()->quizzes.saveTemplate(stranger,null,config(false,false,false,item(choice,1)))).isInstanceOf(BusinessException.class);
        questions.auditQuestion(choice,1,true);
        assertThat(quizzes.saveTemplate(stranger,null,config(false,false,false,item(choice,1))).get("questionCount")).isEqualTo(1L);
    }
    @Test void cannotPublishOutsideClassOrHaveTwoActiveQuizzes(){
        long t=template(config(false,false,false,item(choice,1)));quizzes.publish(teacher,new Publish(t,session));
        assertThatThrownBy(()->quizzes.publish(teacher,new Publish(t,session))).hasMessageContaining("先结束当前小测");
        assertThatThrownBy(()->quizzes.publish(teacher+1,new Publish(t,session))).isInstanceOf(BusinessException.class);
        classroom.end(teacher,session);
        assertThatThrownBy(()->quizzes.publish(teacher,new Publish(t,session))).hasMessageContaining("已结束");
    }
    @Test void answersHiddenUntilEndAndCannotLeakThroughListOrRanking(){
        long id=publish(config(true,false,true,item(choice,1),item(judge,2)));
        var initial=quizzes.detail(studentAuth(a),id);assertThat(map(list(initial,"questions").get(0),"question").get("answer")).isNull();
        var result=quizzes.submit(a,id,answers(new Answer(choice,1,"A"),new Answer(judge,2,"错误")));
        assertThat(result.get("answersVisible")).isEqualTo(false);assertThat(map(result,"student").get("score")).isNull();
        assertThat(list(result,"answers")).allSatisfy(row->{assertThat(row.get("score")).isNull();assertThat(map(row,"question").get("answer")).isNull();});
        assertThat(quizzes.studentRuns(a).get(0).get("score")).isNull();
        assertThatThrownBy(()->quizzes.scores(studentAuth(a),id)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->quizzes.addMistake(a,id,new Mistake(judge,2))).isInstanceOf(BusinessException.class);
        quizzes.end(teacher,id);result=quizzes.detail(studentAuth(a),id);
        assertThat(result.get("answersVisible")).isEqualTo(true);assertThat((BigDecimal)map(result,"student").get("score")).isEqualByComparingTo("5");
        assertThat(map(list(result,"questions").get(0),"question").get("answer")).isEqualTo("A");
        assertThat(quizzes.scores(studentAuth(a),id)).singleElement().satisfies(row->assertThat(row.get("rank")).isEqualTo(1));
        quizzes.addMistake(a,id,new Mistake(judge,2));quizzes.addMistake(a,id,new Mistake(judge,2));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM error_book WHERE student_id=? AND question_id=? AND question_type=2",Integer.class,a,judge)).isEqualTo(1);
    }
    @Test void immediateReviewOnlyAfterOwnSubmissionAndOptionalRankingRespected(){
        long id=publish(config(false,false,false,item(choice,1)));
        var before=quizzes.detail(studentAuth(b),id);assertThat(map(list(before,"questions").get(0),"question").get("answer")).isNull();
        var result=quizzes.submit(a,id,answers(new Answer(choice,1,"A")));
        assertThat(result.get("answersVisible")).isEqualTo(true);
        assertThatThrownBy(()->quizzes.scores(studentAuth(a),id)).isInstanceOf(BusinessException.class);
        assertThat(quizzes.scores(teacherAuth(),id)).hasSize(2);
        assertThatThrownBy(()->quizzes.addMistake(a,id,new Mistake(choice,1))).hasMessageContaining("错题");
    }
    @Test void deadlineAutoSubmitsSavedDraftAndNeverOverwritesSubmittedAnswers(){
        long id=publish(config(true,false,true,item(choice,1)));
        quizzes.draft(a,id,answers(new Answer(choice,1,"B")));
        assertThat(quizzes.pending(a)).hasSize(1);
        db.update("UPDATE classroom_quiz SET deadline=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",id);
        quizzes.expire();var result=quizzes.detail(studentAuth(a),id);
        assertThat(result.get("endedAt")).isNotNull();assertThat(map(result,"student").get("status")).isEqualTo("SUBMITTED");
        assertThat(list(result,"answers").get(0).get("studentAnswer")).isEqualTo("B");
        quizzes.submit(a,id,answers(new Answer(choice,1,"A")));
        assertThat(list(quizzes.detail(studentAuth(a),id),"answers").get(0).get("studentAnswer")).isEqualTo("B");
        assertThat(map(quizzes.detail(studentAuth(b),id),"student").get("status")).isEqualTo("ABSENT");assertThat(quizzes.pending(a)).isEmpty();
    }
    @Test void onlyClosedCountedQuizzesAffectCourseScore(){
        long practice=publish(config(false,false,false,item(choice,1)));quizzes.submit(a,practice,answers(new Answer(choice,1,"A")));quizzes.end(teacher,practice);
        assertThat(score()).isEqualByComparingTo("0");
        long id=publish(config(false,true,true,item(choice,1),item(judge,2)));quizzes.submit(a,id,answers(new Answer(choice,1,"A"),new Answer(judge,2,"错误")));
        courses.recalculateAndUpdateStudentScore(course,a);assertThat(score()).isEqualByComparingTo("0");
        quizzes.end(teacher,id);assertThat(score()).isEqualByComparingTo("50");
    }
    @Test void manualGradingUpdatesTotalAndRejectsOutOfRangeOrWrongTeacher(){
        long id=publish(config(false,true,true,item(choice,1),item(solve,3)));
        quizzes.submit(a,id,answers(new Answer(choice,1,"A"),new Answer(solve,3,"我的解答是4")));quizzes.end(teacher,id);
        assertThat(score()).isEqualByComparingTo("50");assertThat(quizzes.scores(studentAuth(a),id)).isEmpty();
        assertThatThrownBy(()->quizzes.grade(teacher+1,id,new Grade(a,solve,BigDecimal.ONE,""))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->quizzes.grade(teacher,id,new Grade(a,solve,new BigDecimal("6"),""))).hasMessageContaining("超过");
        quizzes.grade(teacher,id,new Grade(a,solve,new BigDecimal("3"),"步骤部分正确"));
        assertThat(score()).isEqualByComparingTo("80");assertThat(quizzes.scores(studentAuth(a),id)).hasSize(1);
        assertThat(list(quizzes.teacherReview(teacher,id,a),"answers")).hasSize(2);
    }
    @Test void questionAndTemplateSnapshotsSurviveEditsAndDeletion(){
        long t=template(config(false,false,false,item(choice,1)));long id=((Number)quizzes.publish(teacher,new Publish(t,session)).get("id")).longValue();
        quizzes.saveTemplate(teacher,t,new Template("修改后的模板",null,false,false,false,List.of(item(judge,2))));
        quizzes.deleteTemplate(teacher,t);questions.deleteQuestion(choice,1);
        var run=quizzes.detail(studentAuth(a),id);assertThat(run.get("name")).isEqualTo("单元巩固小测");assertThat(list(run,"questions")).hasSize(1);
        assertThat(map(list(run,"questions").get(0),"question").get("lost")).isEqualTo(false);
        var result=quizzes.submit(a,id,answers(new Answer(choice,1,"A")));
        assertThat((BigDecimal)map(result,"student").get("score")).isEqualByComparingTo("5");
    }
    @Test void onlyRosterStudentsCanSeeOrSubmitAndMalformedAnswersRejected(){
        long id=publish(config(false,false,false,item(choice,1)));long c=student();
        assertThatThrownBy(()->quizzes.detail(studentAuth(c),id)).isInstanceOf(BusinessException.class);
        courses.joinCourse(c,course);assertThatThrownBy(()->quizzes.submit(c,id,answers())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->quizzes.submit(a,id,answers(new Answer(choice,1,"A"),new Answer(choice,1,"B")))).hasMessageContaining("重复");
        assertThatThrownBy(()->quizzes.submit(a,id,answers(new Answer(choice+100000,1,"A")))).hasMessageContaining("不属于");
        assertThatThrownBy(()->quizzes.draft(a,id,answers(new Answer(choice,1,"A".repeat(10001))))).hasMessageContaining("10000");
    }
    @Test void endingClassClosesQuizzesAndCourseDeletionCascadesAllAnswers(){
        long id=publish(config(true,true,true,item(choice,1)));quizzes.draft(a,id,answers(new Answer(choice,1,"A")));
        classroom.end(teacher,session);assertThat(quizzes.detail(studentAuth(a),id).get("endedAt")).isNotNull();assertThat(score()).isEqualByComparingTo("100");
        courses.deleteCourseByTeacher(teacher,course);
        for(String table:List.of("classroom_quiz_question","classroom_quiz_student","classroom_quiz_answer"))assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE quiz_id=?",Integer.class,id)).isZero();
    }
    @Test void rankingHandlesTiesAndMultipleChoiceUsesSets(){
        long multi=question(1,"A,C");long id=publish(config(false,false,true,item(multi,1)));
        assertThat(map(list(quizzes.detail(studentAuth(a),id),"questions").get(0),"question").get("multiple")).isEqualTo(true);
        quizzes.submit(a,id,answers(new Answer(multi,1,"C,A")));quizzes.submit(b,id,answers(new Answer(multi,1,"A,C")));
        assertThat(quizzes.scores(studentAuth(a),id)).hasSize(2).allSatisfy(r->assertThat(r.get("rank")).isEqualTo(1));
    }
    @Test void studentsCannotBypassHiddenQuizAnswersViaTeacherQuestionBank(){
        AuthContext.set(studentAuth(a));
        assertThatThrownBy(()->questionController.publicQuestions()).isInstanceOf(BusinessException.class).hasMessageContaining("教师组卷");
        AuthContext.set(teacherAuth());assertThat(questionController.publicQuestions().getCode()).isEqualTo(200);
    }
}

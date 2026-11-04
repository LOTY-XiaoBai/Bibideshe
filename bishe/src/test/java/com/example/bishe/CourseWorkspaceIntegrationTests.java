package com.example.bishe;

import com.example.bishe.common.*;
import com.example.bishe.dto.*;
import com.example.bishe.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="app.token.secret=test-token-secret-with-at-least-32-characters")
@EnabledIfEnvironmentVariable(named="RUN_DB_INTEGRATION",matches="true")
@Transactional
class CourseWorkspaceIntegrationTests {
 @Autowired CourseService courses;
 @Autowired CourseWorkspaceService workspace;
 @Autowired CourseFilesService files;
 @Autowired FileUploadService storage;
 @Autowired TeacherEvaluationService evaluations;
 @Autowired ScoreCompareService ranking;
 @Autowired StudyMistakeService mistakes;
 @Autowired ExamService exams;
 @Autowired HomeworkService homeworks;
 @Autowired QuizService quizzes;
 @Autowired ClassroomService classroom;
 @Autowired CheckInService attendance;
 @Autowired QuestionService questions;
 @Autowired JdbcTemplate db;
 long teacher,a,b,c,course,choice,judge;
 long id(){return 5_000_000_000_000L+ThreadLocalRandom.current().nextLong(1_000_000_000_000L);}
 long student(){long n=id();db.update("INSERT INTO student(student_id,nickname,password) VALUES(?,'课程页面测试学生','disabled-test')",n);return n;}
 AuthUser studentAuth(long n){return new AuthUser(n,""+n,UserRole.STUDENT);}
 AuthUser teacherAuth(){return new AuthUser(teacher,""+teacher,UserRole.TEACHER);}
 CourseCreateRequest request(String name){var r=new CourseCreateRequest();r.setTeacherId(teacher);r.setCourseName(name);return r;}
 @BeforeEach void setup(){teacher=id();db.update("INSERT INTO teacher(teacher_id,name,password) VALUES(?,'课程页面测试教师','disabled-test')",teacher);a=student();b=student();c=student();course=courses.createCourse(request("课程页面回归")).getCourseId();courses.joinCourse(a,course);courses.joinCourse(b,course);choice=question(1,"A");judge=question(2,"正确");}
 long question(int type,String answer){var r=new QuestionCreateRequest();r.setPublisherId(teacher);r.setContent("课程页面题目"+UUID.randomUUID());r.setQuestionType(type);r.setAnswer(answer);r.setOptions(List.of("1","2","3","4"));return questions.createQuestion(r).getQuestionId();}
 QuestionScoreItem item(long id,int type){var q=new QuestionScoreItem();q.setQuestionId(id);q.setQuestionType(type);q.setScore(new BigDecimal("5"));return q;}
 AnswerItem answer(long id,int type,String value){var a=new AnswerItem();a.setQuestionId(id);a.setQuestionType(type);a.setStudentAnswer(value);return a;}
 long exam(boolean hidden){var r=new ExamPublishRequest();r.setTeacherId(teacher);r.setCourseId(course);r.setTimeLimit(30);r.setHideResultUntilPublish(hidden?1:0);r.setQuestions(List.of(item(choice,1),item(judge,2)));return exams.publishExam(r).getExamId();}
 void submitExam(long id){exams.startExam(a,id);var r=new ExamSubmitRequest();r.setExamId(id);r.setStudentId(a);r.setAnswers(List.of(answer(choice,1,"A"),answer(judge,2,"")));exams.submitExam(r);}
 long homework(boolean hidden){var r=new HomeworkPublishRequest();r.setTeacherId(teacher);r.setCourseId(course);r.setDeadline(LocalDateTime.now().plusHours(1));r.setHideResultUntilPublish(hidden?1:0);r.setQuestions(List.of(item(choice,1),item(judge,2)));return homeworks.publishHomework(r).getHomeworkId();}
 void submitHomework(long id){var r=new HomeworkSubmitRequest();r.setHomeworkId(id);r.setStudentId(a);r.setAnswers(List.of(answer(choice,1,"A"),answer(judge,2,"正确")));homeworks.submitHomework(r);}
 StudyMistakeService.Batch batch(String kind,long id){return new StudyMistakeService.Batch(kind,id,List.of(new StudyMistakeService.Ref(choice,1),new StudyMistakeService.Ref(judge,2)));}

 @Test void teacherCanHaveFiveActiveCoursesAndRestoringCannotExceedLimit(){
  for(int i=0;i<4;i++)courses.createCourse(request("课程"+i));
  assertThatThrownBy(()->courses.createCourse(request("第六门"))).hasMessageContaining("5门");
  courses.finishCourse(teacher,course);courses.createCourse(request("替补课程"));
  assertThatThrownBy(()->courses.unfinishCourse(teacher,course)).hasMessageContaining("最多5门");
 }
 @Test void settingsPreserveDataAndRejectCapacityBelowMembership(){
  var r=request("新课程名称");r.setFixedSchedule(true);r.setClassWeekday(3);r.setClassTime("19:30");r.setApprovalRequired(true);r.setMaxStudents(2);r.setCoverUrl("/uploads/image/"+"a".repeat(32)+".png");
  var saved=workspace.update(teacher,course,r);assertThat(saved.getCourseName()).isEqualTo("新课程名称");assertThat(saved.getClassTime()).isEqualTo("19:30");assertThat(saved.getShowStudentRanking()).isTrue();
  r.setMaxStudents(1);assertThatThrownBy(()->workspace.update(teacher,course,r)).hasMessageContaining("不能低于");
  r.setMaxStudents(null);assertThat(workspace.update(teacher,course,r).getMaxStudents()).isNull();
  assertThatThrownBy(()->workspace.update(teacher+1,course,r)).isInstanceOf(BusinessException.class);
 }
 @Test void rankingOffMasksOtherScoresAndUsesJoinOrderOnEveryStudentMemberRead(){
  db.update("UPDATE course_personnel SET joined_at='2026-01-01 09:00:00' WHERE course_id=? AND student_id=?",course,a);db.update("UPDATE course_personnel SET joined_at='2026-01-01 10:00:00' WHERE course_id=? AND student_id=?",course,b);
  courses.updateStudentScore(course,a,new BigDecimal("20"));courses.updateStudentScore(course,b,new BigDecimal("90"));
  assertThat(workspace.members(studentAuth(a),course).get(0).getStudentId()).isEqualTo(b);
  workspace.setRanking(teacher,course,false);var members=workspace.members(studentAuth(a),course);
  assertThat(members).extracting(com.example.bishe.dto.CourseMemberVO::getStudentId).containsExactly(a,b);assertThat(members.get(0).getStudentScore()).isEqualByComparingTo("20");assertThat(members.get(1).getStudentScore()).isNull();
  assertThat(workspace.members(teacherAuth(),course).get(1).getStudentScore()).isEqualByComparingTo("90");
  assertThatThrownBy(()->ranking.classRanking(a,course)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->workspace.members(studentAuth(c),course)).isInstanceOf(BusinessException.class);
 }
 @Test void dismissingApplicationDoesNotCancelItAndReapplicationBecomesVisible(){
  var r=request("需审核课程");r.setApprovalRequired(true);long id=courses.createCourse(r).getCourseId();courses.joinCourse(c,id);workspace.dismissApplications(c);
  assertThat(((Number)courses.applications(c,false).get(0).get("studentHidden")).intValue()).isEqualTo(1);assertThat(workspace.applications(teacher,id)).hasSize(1);
  courses.reviewApplication(teacher,id,c,false);courses.joinCourse(c,id);assertThat(((Number)courses.applications(c,false).get(0).get("studentHidden")).intValue()).isZero();
 }
 @Test void teacherRatingRequiresFinishedCourseAndCannotBeChanged(){
  var r=new TeacherRateRequest();r.setTeacherId(teacher);r.setCourseId(course);r.setScore(new BigDecimal("8.5"));
  assertThatThrownBy(()->evaluations.rateTeacher(a,r)).hasMessageContaining("结课后");courses.finishCourse(teacher,course);evaluations.rateTeacher(a,r);
  assertThat(evaluations.getCourseTeacher(a,course).getMyScore()).isEqualByComparingTo("8.5");assertThatThrownBy(()->evaluations.rateTeacher(a,r)).hasMessageContaining("重复评分");
 }
 @Test void materialsArePrivateMetadataAndRejectNonmembersAndDisguisedFiles() throws Exception {
  var file=new MockMultipartFile("file","讲义.pdf","application/pdf","%PDF-1.4\ncourse-test".getBytes(java.nio.charset.StandardCharsets.UTF_8));long material=((Number)files.upload(teacher,course,file).get("id")).longValue();
  var entries=files.list(studentAuth(a),course);assertThat(entries).hasSize(1);assertThat(entries.get(0)).doesNotContainKeys("storage_name","storageName","url");
  var downloaded=files.download(studentAuth(a),course,material);try(var input=downloaded.resource().getInputStream()){assertThat(input.readAllBytes()).isEqualTo(file.getBytes());}
  assertThatThrownBy(()->files.download(studentAuth(c),course,material)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->files.upload(teacher+1,course,file)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->files.upload(teacher,course,new MockMultipartFile("file","fake.pdf","application/pdf","MZ-executable".getBytes()))).hasMessageContaining("不匹配");
  assertThatThrownBy(()->storage.courseMaterialPath("../other.pdf")).isInstanceOf(BusinessException.class);files.remove(teacher,course,material);assertThat(files.list(studentAuth(a),course)).isEmpty();
 }
 @Test void examBatchCanIncludeCorrectAndUnansweredQuestionsButCannotLeakHiddenResults(){
  long exam=exam(true);exams.startExam(a,exam);assertThat(exams.listRecordsByStudent(a).get(0).getSubmitted()).isFalse();
  assertThatThrownBy(()->mistakes.add(a,batch("EXAM",exam))).isInstanceOf(BusinessException.class);submitExam(exam);
  assertThat(exams.listRecordsByStudent(a).get(0).getSubmitted()).isTrue();assertThat(exams.listRecordsByStudent(a).get(0).getTeacherScore()).isNull();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM error_book WHERE student_id=?",Integer.class,a)).isZero();
  assertThat(mistakes.savedAnswers(a,"exam",exam)).hasSize(2).allSatisfy(row->assertThat(row).doesNotContainKeys("answer","score"));
  assertThatThrownBy(()->mistakes.add(a,batch("EXAM",exam))).isInstanceOf(BusinessException.class);exams.publishResult(teacher,exam);
  assertThat(mistakes.add(a,batch("EXAM",exam)).get("selectedCount")).isEqualTo(2);assertThat(mistakes.add(a,batch("EXAM",exam)).get("addedCount")).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM error_book WHERE student_id=?",Integer.class,a)).isEqualTo(2);
 }
 @Test void homeworkAndQuizUseSameBatchSelectionAndRejectForeignQuestions(){
  long hw=homework(true);submitHomework(hw);assertThat(homeworks.listRecordsByStudent(a).get(0).getTeacherScore()).isNull();assertThatThrownBy(()->mistakes.add(a,batch("HOMEWORK",hw))).isInstanceOf(BusinessException.class);
  homeworks.publishResult(teacher,hw);assertThat(mistakes.add(a,batch("HOMEWORK",hw)).get("selectedCount")).isEqualTo(2);
  assertThatThrownBy(()->mistakes.add(a,new StudyMistakeService.Batch("HOMEWORK",hw,List.of(new StudyMistakeService.Ref(choice+100000,1))))).isInstanceOf(BusinessException.class);
  String session=classroom.start(teacher,course,"LOCAL").get("sessionId").toString();var config=new QuizRequests.Template("批量收藏测试",null,false,false,false,List.of(new QuizRequests.Item(choice,1,BigDecimal.ONE),new QuizRequests.Item(judge,2,BigDecimal.ONE)));
  long t=((Number)quizzes.saveTemplate(teacher,null,config).get("id")).longValue(),quiz=((Number)quizzes.publish(teacher,new QuizRequests.Publish(t,session)).get("id")).longValue();
  quizzes.submit(a,quiz,new QuizRequests.Submit(List.of(new QuizRequests.Answer(choice,1,"A"))));assertThat(mistakes.add(a,batch("QUIZ",quiz)).get("selectedCount")).isEqualTo(2);
 }
 @Test void personalSummaryCountsActualSubmissionsAttendanceAndQuizzes(){
  long exam=exam(false),hw=homework(false);submitExam(exam);submitHomework(hw);
  String session=classroom.start(teacher,course,"LOCAL").get("sessionId").toString();var config=new QuizRequests.Template("参与统计",null,false,false,false,List.of(new QuizRequests.Item(choice,1,BigDecimal.ONE)));
  long template=((Number)quizzes.saveTemplate(teacher,null,config).get("id")).longValue(),quiz=((Number)quizzes.publish(teacher,new QuizRequests.Publish(template,session)).get("id")).longValue();quizzes.submit(a,quiz,new QuizRequests.Submit(List.of(new QuizRequests.Answer(choice,1,"A"))));
  var check=new CheckInPublishRequest();check.setTeacherId(teacher);check.setCourseId(course);check.setDeadline(LocalDateTime.now().plusMinutes(5));check.setScoreValue(BigDecimal.ONE);long checkId=attendance.publishCheckIn(check).getCheckInId();attendance.studentCheckIn(checkId,a);
  var summary=workspace.summary(a,course);for(String key:List.of("quizCount","checkInCount","examCount","homeworkCount"))assertThat(summary.get(key)).isEqualTo(1);
  assertThat(workspace.summary(b,course).get("examCount")).isEqualTo(0);assertThatThrownBy(()->workspace.summary(c,course)).isInstanceOf(BusinessException.class);
 }
}

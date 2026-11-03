package com.example.bishe;

import com.example.bishe.common.*;
import com.example.bishe.dto.*;
import com.example.bishe.entity.Course;
import com.example.bishe.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="app.token.secret=test-token-secret-with-at-least-32-characters")
@EnabledIfEnvironmentVariable(named="RUN_DB_INTEGRATION", matches="true")
@Transactional
class CourseClassroomIntegrationTests {
    @Autowired CourseService courses;
    @Autowired ClassroomService classroom;
    @Autowired CheckInService checkIns;
    @Autowired JdbcTemplate db;
    long teacher, a, b;
    long id() { return 7_000_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000_000L); }
    long student() { long id=id(); db.update("INSERT INTO student(student_id,nickname,password,avatar) VALUES(?,'课堂测试学生','disabled-test-account','/test-avatar.png')", id); return id; }
    @BeforeEach void setup() {
        teacher=id(); db.update("INSERT INTO teacher(teacher_id,name,password) VALUES(?,'课堂测试教师','disabled-test-account')", teacher);
        a=student(); b=student();
    }
    CourseCreateRequest request(boolean approval, Integer max) {
        var r=new CourseCreateRequest(); r.setTeacherId(teacher); r.setCourseName("课堂集成测试"); r.setApprovalRequired(approval); r.setMaxStudents(max); return r;
    }
    AuthUser studentAuth(long id) { return new AuthUser(id, ""+id, UserRole.STUDENT); }
    CheckInPublishRequest attendance(Long courseId) {
        var r=new CheckInPublishRequest(); r.setTeacherId(teacher); r.setCourseId(courseId); r.setDeadline(LocalDateTime.now().plusMinutes(5)); r.setScoreValue(BigDecimal.ONE); return r;
    }
    @Test void settingsRoundTripAndValidation() {
        var r=request(true,25); r.setFixedSchedule(true); r.setClassWeekday(7); r.setClassTime("09:30");
        Course c=courses.createCourse(r), saved=courses.searchCourse(c.getCourseId());
        assertThat(saved.getApprovalRequired()).isTrue(); assertThat(saved.getMaxStudents()).isEqualTo(25); assertThat(saved.getClassWeekday()).isEqualTo(7); assertThat(saved.getClassTime()).isEqualTo("09:30");
        r.setClassTime("25:00"); assertThatThrownBy(()->courses.createCourse(r)).hasMessageContaining("星期和时间");
        var invalid=request(false,0); assertThatThrownBy(()->courses.createCourse(invalid)).hasMessageContaining("人数上限");
    }
    @Test void approvalDoesNotEnrollUntilAcceptedAndCapacityIsCheckedAgain() {
        Long id=courses.createCourse(request(true,1)).getCourseId();
        assertThat(courses.joinCourse(a,id)).contains("申请已提交"); courses.joinCourse(b,id);
        assertThat(courses.listMembers(id)).isEmpty(); assertThat(courses.applications(teacher,true)).hasSize(2);
        assertThatThrownBy(()->courses.joinCourse(a,id)).hasMessageContaining("等待教师审核");
        assertThatThrownBy(()->courses.reviewApplication(teacher+1,id,a,true)).isInstanceOf(BusinessException.class);
        courses.reviewApplication(teacher,id,a,true);
        assertThat(courses.listMembers(id)).singleElement().satisfies(m->assertThat(m.getAvatar()).isEqualTo("/test-avatar.png"));
        assertThatThrownBy(()->courses.reviewApplication(teacher,id,b,true)).hasMessageContaining("人数已满");
        courses.reviewApplication(teacher,id,b,false);
        assertThat(courses.applications(b,false).get(0).get("status")).isEqualTo("REJECTED");
        courses.leaveCourse(a,id); assertThat(courses.joinCourse(b,id)).contains("申请已提交"); courses.reviewApplication(teacher,id,b,true);
        assertThat(courses.listMembers(id)).hasSize(1);
    }
    @Test void checkInRequiresClassAndEndingClassClosesCheckIn() {
        Long id=courses.createCourse(request(false,null)).getCourseId(); courses.joinCourse(a,id);
        assertThatThrownBy(()->checkIns.publishCheckIn(attendance(id))).hasMessageContaining("先开始上课");
        var session=classroom.start(teacher,id,"LOCAL");
        assertThat(classroom.activeForStudent(a)).hasSize(1); assertThat(classroom.activeForStudent(b)).isEmpty();
        assertThatThrownBy(()->classroom.start(teacher,id,"LOCAL")).hasMessageContaining("正在上课");
        var check=checkIns.publishCheckIn(attendance(id)); assertThat(classroom.pendingCheckIns(a)).hasSize(1);
        checkIns.studentCheckIn(check.getCheckInId(),a); assertThat(classroom.pendingCheckIns(a)).isEmpty();
        classroom.end(teacher,session.get("sessionId").toString()); assertThat(classroom.active(id)).isNull();
        assertThat(db.queryForObject("SELECT status FROM check_in_record WHERE check_in_id=?",Integer.class,check.getCheckInId())).isEqualTo(1);
        assertThatThrownBy(()->checkIns.publishCheckIn(attendance(id))).hasMessageContaining("先开始上课");
    }
    @Test void onlineSignalsRejectOutsidersImpersonationAndExpiredSessions() {
        Long id=courses.createCourse(request(false,null)).getCourseId(); courses.joinCourse(a,id);
        String session=classroom.start(teacher,id,"ONLINE").get("sessionId").toString();
        classroom.signal(studentAuth(a),session,teacher,"JOIN",Map.of());
        assertThatThrownBy(()->classroom.signal(studentAuth(b),session,teacher,"JOIN",Map.of())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->classroom.signal(studentAuth(a),session,teacher,"OFFER",Map.of())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->classroom.signal(studentAuth(a),session,b,"JOIN",Map.of())).isInstanceOf(BusinessException.class);
        db.update("UPDATE course_session SET heartbeat_at=DATE_SUB(NOW(),INTERVAL 61 SECOND) WHERE session_id=?",session);
        assertThat(classroom.active(id)).isNull(); assertThat(classroom.activeForStudent(a)).isEmpty();
        assertThatThrownBy(()->classroom.heartbeat(teacher,session)).hasMessageContaining("已结束");
        assertThatThrownBy(()->classroom.signal(studentAuth(a),session,teacher,"JOIN",Map.of())).hasMessageContaining("已结束");
        assertThat(classroom.start(teacher,id,"LOCAL").get("sessionId")).isNotEqualTo(session);
    }
    @Test void finishAndDeleteCleanUpClassroomAndApplications() {
        Long id=courses.createCourse(request(true,null)).getCourseId(); courses.joinCourse(a,id); courses.reviewApplication(teacher,id,a,true); courses.joinCourse(b,id);
        classroom.start(teacher,id,"LOCAL"); courses.finishCourse(teacher,id); assertThat(classroom.active(id)).isNull();
        assertThatThrownBy(()->classroom.start(teacher,id,"LOCAL")).hasMessageContaining("已结课");
        courses.deleteCourseByTeacher(teacher,id);
        for(String table:List.of("course_application","course_session","course_personnel")) assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE course_id=?",Integer.class,id)).isZero();
    }
    @Test void staleSessionCannotEndNewClassAndExpiredClassCannotAcceptCheckIns() {
        Long id=courses.createCourse(request(false,null)).getCourseId(); courses.joinCourse(a,id);
        String first=classroom.start(teacher,id,"LOCAL").get("sessionId").toString();
        var attendance=checkIns.publishCheckIn(attendance(id));
        db.update("UPDATE course_session SET heartbeat_at=DATE_SUB(NOW(),INTERVAL 61 SECOND) WHERE session_id=?",first);
        assertThat(classroom.pendingCheckIns(a)).isEmpty();
        assertThatThrownBy(()->checkIns.studentCheckIn(attendance.getCheckInId(),a)).hasMessageContaining("签到已结束");
        String next=classroom.start(teacher,id,"LOCAL").get("sessionId").toString();
        classroom.end(teacher,first);
        assertThat(classroom.active(id).get("sessionId")).isEqualTo(next);
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentJoinsCannotExceedCapacity() throws Exception {
        Long courseId=null; var executor=Executors.newFixedThreadPool(2);
        try {
            courseId=courses.createCourse(request(false,1)).getCourseId(); final Long id=courseId;
            var latch=new CountDownLatch(1);
            Callable<Boolean> first=()->{latch.await();try{courses.joinCourse(a,id);return true;}catch(BusinessException e){return false;}};
            Callable<Boolean> second=()->{latch.await();try{courses.joinCourse(b,id);return true;}catch(BusinessException e){return false;}};
            var one=executor.submit(first); var two=executor.submit(second); latch.countDown();
            assertThat(List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(courses.listMembers(courseId)).hasSize(1);
        } finally {
            executor.shutdownNow(); if(courseId!=null) courses.deleteCourseByTeacher(teacher,courseId);
            db.update("DELETE FROM student WHERE student_id IN (?,?)",a,b); db.update("DELETE FROM teacher WHERE teacher_id=?",teacher);
        }
    }
}

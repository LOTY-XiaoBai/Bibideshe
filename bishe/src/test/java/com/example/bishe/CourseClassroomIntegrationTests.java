package com.example.bishe;

import com.example.bishe.common.*;
import com.example.bishe.dto.*;
import com.example.bishe.entity.Course;
import com.example.bishe.service.*;
import com.example.bishe.util.TokenUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"app.token.secret=test-token-secret-with-at-least-32-characters",
        "app.merchant.amap-key=attendance-test-key"})
@EnabledIfEnvironmentVariable(named="RUN_DB_INTEGRATION", matches="true")
@Transactional
class CourseClassroomIntegrationTests {
    @Autowired CourseService courses;
    @Autowired ClassroomService classroom;
    @Autowired CheckInService checkIns;
    @Autowired JdbcTemplate db;
    @Autowired WebApplicationContext web;
    @Autowired ObjectMapper json;
    private static HttpServer amap;

    @DynamicPropertySource
    static void mockMap(DynamicPropertyRegistry properties) throws java.io.IOException {
        amap = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        amap.createContext("/v3/assistant/coordinate/convert", exchange -> {
            String result = exchange.getRequestURI().getQuery().contains("116.290000,39.800000")
                    ? "116.300000,39.800000" : "116.410000,39.800000";
            byte[] body = ("{\"status\":\"1\",\"locations\":\"" + result + "\"}").getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        amap.start();
        properties.add("app.amap.rest-base-url", () -> "http://127.0.0.1:" + amap.getAddress().getPort());
    }

    @AfterAll static void stopMap() { if (amap != null) amap.stop(0); }
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
    @Test void codeCheckInRejectsSubmissionWithoutTheCode() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(web).build();
        long checkInId = publish(mvc, startClassWithStudent(), Map.of("mode", "CODE"));
        mvc.perform(sign(checkInId, Map.of())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/check-in/display/{id}", checkInId).header("Authorization", bearer(studentAuth(a))))
                .andExpect(status().isForbidden());
        String code = display(mvc, checkInId).path("code").asText();
        assertThat(code).matches("[0-9]{4}");
        mvc.perform(sign(checkInId, Map.of("code", code))).andExpect(status().isOk());
    }

    @Test void previousCodeWorksBrieflyAfterRotationAndThenExpires() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(web).build();
        long courseId = startClassWithStudent();
        courses.joinCourse(b, courseId);
        long checkInId = publish(mvc, courseId, Map.of("mode", "CODE"));
        var initial = display(mvc, checkInId);
        String previous = initial.path("code").asText();
        Thread.sleep(initial.path("refreshMs").asLong() + 100);
        assertThat(display(mvc, checkInId).path("code").asText()).isNotEqualTo(previous);
        mvc.perform(sign(checkInId, Map.of("code", previous))).andExpect(status().isOk());

        Thread.sleep(2_100);
        mvc.perform(signAs(b, checkInId, Map.of("code", previous))).andExpect(status().isBadRequest());
        String current = display(mvc, checkInId).path("code").asText();
        mvc.perform(post("/api/check-in/end").param("checkInId", Long.toString(checkInId))
                .header("Authorization", teacherBearer())).andExpect(status().isOk());
        mvc.perform(signAs(b, checkInId, Map.of("code", current))).andExpect(status().isBadRequest());
        assertThat(display(mvc, checkInId).has("code")).isFalse();
    }

    @Test void gestureCheckInRequiresTheDisplayedSequenceInOrder() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(web).build();
        long checkInId = publish(mvc, startClassWithStudent(),
                Map.of("mode", "GESTURE", "gesture", List.of(1, 2, 3, 6)));
        mvc.perform(get("/api/check-in/display/{id}", checkInId).header("Authorization", teacherBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.gesture[0]").value(1))
                .andExpect(jsonPath("$.data.gesture[3]").value(6));
        mvc.perform(sign(checkInId, Map.of("gesture", List.of(6, 3, 2, 1)))).andExpect(status().isBadRequest());
        mvc.perform(sign(checkInId, Map.of("gesture", List.of(1, 2, 3, 6)))).andExpect(status().isOk());
        assertThat(classroom.pendingCheckIns(a)).isEmpty();
    }

    @Test void locationCheckInUsesTheSavedRectangleAndChecksTheConvertedPoint() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(web).build();
        long courseId = startClassWithStudent();
        long checkInId = publish(mvc, courseId,
                Map.of("mode", "LOCATION", "bounds", Map.of("west", 116.3, "south", 39.7, "east", 116.4, "north", 39.9)));
        mvc.perform(get("/api/check-in/location-default/{courseId}", courseId).header("Authorization", teacherBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.west").value(116.3))
                .andExpect(jsonPath("$.data.north").value(39.9));
        var outside = Map.of("latitude", 39.8, "longitude", 116.40, "accuracy", 77);
        mvc.perform(post("/api/check-in/location-preview/{id}", checkInId).header("Authorization", bearer(studentAuth(a)))
                        .contentType("application/json").content(json.writeValueAsString(outside)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.inside").value(false));
        mvc.perform(sign(checkInId, Map.of("position", outside)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("当前位置在签到范围外"));
        var position = Map.of("latitude", 39.8, "longitude", 116.29, "accuracy", 77);
        mvc.perform(get("/api/location/map-config").param("checkInId", Long.toString(checkInId))
                        .header("Authorization", bearer(studentAuth(b))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("您未加入该课程, 无法签到"));
        mvc.perform(post("/api/check-in/location-preview/{id}", checkInId).header("Authorization", bearer(studentAuth(a)))
                        .contentType("application/json").content(json.writeValueAsString(position)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.inside").value(true))
                .andExpect(jsonPath("$.data.point.longitude").value(116.3));
        assertThat(classroom.pendingCheckIns(a)).hasSize(1);
        mvc.perform(sign(checkInId, Map.of("position", position)))
                .andExpect(status().isOk());
    }

    private long startClassWithStudent() {
        long courseId = courses.createCourse(request(false, null)).getCourseId();
        courses.joinCourse(a, courseId);
        classroom.start(teacher, courseId, "LOCAL");
        return courseId;
    }

    private long publish(MockMvc mvc, long courseId, Map<String, Object> configuration) throws Exception {
        var body = new HashMap<>(configuration);
        body.put("courseId", courseId);
        body.put("deadline", LocalDateTime.now().plusMinutes(5));
        body.put("scoreValue", 1);
        var published = mvc.perform(post("/api/check-in/publish")
                .header("Authorization", teacherBearer())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(published.getResponse().getContentAsString()).path("data").path("checkInId").asLong();
    }

    private tools.jackson.databind.JsonNode display(MockMvc mvc, long checkInId) throws Exception {
        var result = mvc.perform(get("/api/check-in/display/{id}", checkInId).header("Authorization", teacherBearer()))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private MockHttpServletRequestBuilder sign(long checkInId, Map<String, Object> credential) {
        return signAs(a, checkInId, credential);
    }

    private MockHttpServletRequestBuilder signAs(long student, long checkInId, Map<String, Object> credential) {
        var body = new HashMap<>(credential);
        body.put("checkInId", checkInId);
        return post("/api/check-in/sign").header("Authorization", bearer(studentAuth(student)))
                .contentType("application/json")
                .content(json.writeValueAsString(body));
    }

    private String teacherBearer() { return bearer(new AuthUser(teacher, Long.toString(teacher), UserRole.TEACHER)); }
    private String bearer(AuthUser actor) { return "Bearer " + TokenUtil.createToken(actor); }
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

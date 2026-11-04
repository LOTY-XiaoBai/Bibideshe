package com.example.bishe.service;

import com.example.bishe.common.*;
import com.example.bishe.entity.Course;
import com.example.bishe.mapper.CourseMapper;
import com.example.bishe.mapper.CoursePersonnelMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

/** 持久化课堂状态；直播媒体经 WebRTC 传输，信令只推送给有权限的师生。 */
@Service
public class ClassroomService {
    private final JdbcTemplate jdbc;
    private final CourseMapper courses;
    private final CoursePersonnelMapper members;
    private final SimpMessagingTemplate messaging;
    private final org.springframework.context.ApplicationEventPublisher events;

    public ClassroomService(JdbcTemplate jdbc, CourseMapper courses, CoursePersonnelMapper members, SimpMessagingTemplate messaging, org.springframework.context.ApplicationEventPublisher events) {
        this.jdbc = jdbc; this.courses = courses; this.members = members; this.messaging = messaging;
        this.events = events;
    }

    public Course owned(Long teacherId, Long courseId) {
        Course c = courses.findById(courseId);
        if (c == null || !Objects.equals(c.getTeacherId(), teacherId)) throw new BusinessException(403, "无权操作该课程");
        return c;
    }

    public void accessible(AuthUser user, Long courseId) {
        Course c = courses.findById(courseId);
        if (c == null) throw new BusinessException("课程不存在");
        boolean teacher = UserRole.TEACHER.equals(user.getRole()) && Objects.equals(c.getTeacherId(), user.getUserId());
        boolean student = UserRole.STUDENT.equals(user.getRole()) && members.find(courseId, user.getUserId()) != null;
        if (!teacher && !student) throw new BusinessException(403, "您不在该课程中");
    }

    public Map<String, Object> active(Long courseId) {
        var rows = jdbc.queryForList("SELECT s.session_id AS sessionId,s.course_id AS courseId,s.mode,s.started_at AS startedAt,c.course_name AS courseName,c.teacher_id AS teacherId FROM course_session s JOIN course c ON c.course_id=s.course_id WHERE s.course_id=? AND s.ended_at IS NULL AND s.heartbeat_at > DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 60 SECOND) AND c.is_finished=0 ORDER BY s.started_at DESC LIMIT 1", courseId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> requireSession(AuthUser user, String sessionId) {
        var rows = jdbc.queryForList("SELECT course_id FROM course_session WHERE session_id=?", sessionId);
        if (rows.isEmpty()) throw new BusinessException("课堂不存在");
        Long courseId = ((Number)rows.get(0).get("course_id")).longValue();
        accessible(user, courseId);
        var session = active(courseId);
        if (session == null || !sessionId.equals(session.get("sessionId"))) throw new BusinessException("本次上课已结束");
        return session;
    }

    @Transactional
    public Map<String, Object> start(Long teacherId, Long courseId, String mode) {
        if (!Set.of("LOCAL", "ONLINE").contains(mode == null ? "" : mode)) throw new BusinessException("请选择授课方式");
        courses.findByIdForUpdate(courseId);
        Course c = owned(teacherId, courseId);
        if (Integer.valueOf(1).equals(c.getIsFinished())) throw new BusinessException("课程已结课");
        if (active(courseId) != null) throw new BusinessException("课程正在上课，请进入当前课堂");
        jdbc.update("UPDATE course_session SET ended_at=CURRENT_TIMESTAMP WHERE course_id=? AND ended_at IS NULL", courseId);
        jdbc.update("UPDATE check_in_record SET status=1,end_time=CURRENT_TIMESTAMP WHERE course_id=? AND status=0", courseId);
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO course_session(session_id,course_id,mode) VALUES(?,?,?)", id, courseId, mode);
        var session = active(courseId);
        broadcast(courseId, "CLASS_STARTED", session);
        return session;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void heartbeat(Long teacherId, String sessionId) {
        var rows = jdbc.queryForList("SELECT course_id FROM course_session WHERE session_id=?", sessionId);
        if (rows.isEmpty()) throw new BusinessException("课堂不存在");
        Long courseId = ((Number) rows.get(0).get("course_id")).longValue();
        courses.findByIdForUpdate(courseId);
        owned(teacherId, courseId);
        var session = active(courseId);
        if (session == null || !sessionId.equals(session.get("sessionId"))) throw new BusinessException("本次上课已结束");
        jdbc.update("UPDATE course_session SET heartbeat_at=CURRENT_TIMESTAMP WHERE session_id=? AND ended_at IS NULL", sessionId);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void end(Long teacherId, String sessionId) {
        var rows = jdbc.queryForList("SELECT course_id FROM course_session WHERE session_id=?", sessionId);
        if (rows.isEmpty()) return;
        Long courseId = ((Number)rows.get(0).get("course_id")).longValue();
        courses.findByIdForUpdate(courseId);
        owned(teacherId, courseId);
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM course_session WHERE session_id=? AND ended_at IS NULL", Integer.class, sessionId);
        if (open == null || open == 0) return;
        endForCourse(courseId);
    }

    public void endForCourse(Long courseId) {
        events.publishEvent(new ClassroomEndedEvent(courseId));
        var rows = jdbc.queryForList("SELECT session_id AS sessionId FROM course_session WHERE course_id=? AND ended_at IS NULL", courseId);
        jdbc.update("UPDATE course_session SET ended_at=CURRENT_TIMESTAMP WHERE course_id=? AND ended_at IS NULL", courseId);
        jdbc.update("UPDATE check_in_record SET status=1,end_time=CURRENT_TIMESTAMP WHERE course_id=? AND status=0", courseId);
        for (var row : rows) broadcast(courseId, "CLASS_ENDED", row);
    }

    public List<Map<String, Object>> activeForStudent(Long studentId) {
        return jdbc.queryForList("SELECT s.session_id AS sessionId,s.course_id AS courseId,s.mode,c.course_name AS courseName,c.teacher_id AS teacherId FROM course_session s JOIN course c ON c.course_id=s.course_id JOIN course_personnel p ON p.course_id=c.course_id WHERE p.student_id=? AND s.ended_at IS NULL AND s.heartbeat_at > DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 60 SECOND) AND c.is_finished=0", studentId);
    }

    public List<Map<String, Object>> pendingCheckIns(Long studentId) {
        return jdbc.queryForList("SELECT r.check_in_id AS checkInId,r.course_id AS courseId,c.course_name AS courseName,r.deadline FROM check_in_record r JOIN check_in_user u ON u.check_in_id=r.check_in_id JOIN course c ON c.course_id=r.course_id JOIN course_personnel p ON p.course_id=r.course_id AND p.student_id=u.student_id WHERE u.student_id=? AND u.status=0 AND r.status=0 AND r.deadline>CURRENT_TIMESTAMP AND c.is_finished=0 AND EXISTS(SELECT 1 FROM course_session cs WHERE cs.course_id=r.course_id AND cs.ended_at IS NULL AND cs.heartbeat_at>DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 60 SECOND)) ORDER BY r.deadline", studentId);
    }

    public void signal(AuthUser user, String sessionId, Long targetId, String kind, Map<String, Object> data) {
        var session = requireSession(user, sessionId);
        if (!"ONLINE".equals(session.get("mode"))) throw new BusinessException("本地授课无需直播连接");
        Long teacherId = ((Number)session.get("teacherId")).longValue();
        Long courseId = ((Number)session.get("courseId")).longValue();
        boolean teacher = UserRole.TEACHER.equals(user.getRole()) && Objects.equals(teacherId, user.getUserId());
        if (teacher) {
            if (!Set.of("OFFER", "ICE", "CLOSE").contains(kind == null ? "" : kind) || members.find(courseId, targetId) == null) throw new BusinessException(403, "直播信令无效");
        } else {
            if (!Objects.equals(targetId, teacherId) || !Set.of("JOIN", "ANSWER", "ICE", "LEAVE").contains(kind == null ? "" : kind)) throw new BusinessException(403, "直播信令无效");
        }
        if (data == null || data.toString().length() > 100000) throw new BusinessException("直播信令内容无效");
        push(targetId, "CLASS_SIGNAL", Map.of("sessionId", sessionId, "senderId", user.getUserId(), "kind", kind, "payload", data));
    }

    public void broadcast(Long courseId, String type, Object data) {
        var recipients = members.findByCourseId(courseId).stream().map(p -> p.getStudentId()).toList();
        afterCommit(() -> recipients.forEach(id -> push(id, type, data)));
    }

    private void push(Long id, String type, Object data) {
        messaging.convertAndSendToUser(String.valueOf(id), "/queue/chat", Map.of("type", type, "data", data));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}

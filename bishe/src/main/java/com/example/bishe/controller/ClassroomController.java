package com.example.bishe.controller;

import com.example.bishe.common.*;
import com.example.bishe.service.ClassroomService;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;

@RestController
@RequestMapping("/api/classroom")
public class ClassroomController {
    private final ClassroomService service;
    private final String stunUrl, turnUrl, turnUsername, turnCredential;
    public ClassroomController(ClassroomService service,
            @Value("${app.rtc.stun-url:stun:stun.l.google.com:19302}") String stunUrl,
            @Value("${app.rtc.turn-url:}") String turnUrl,
            @Value("${app.rtc.turn-username:}") String turnUsername,
            @Value("${app.rtc.turn-credential:}") String turnCredential) {
        this.service=service; this.stunUrl=stunUrl; this.turnUrl=turnUrl; this.turnUsername=turnUsername; this.turnCredential=turnCredential;
    }
    public record Start(Long courseId, String mode) {}
    public record Signal(Long targetId, String kind, Map<String,Object> payload) {}
    @PostMapping("/start") public Result<?> start(@RequestBody Start r) { return Result.success(service.start(AuthContext.requireTeacherId(), r.courseId(), r.mode())); }
    @GetMapping("/course/{id}") public Result<?> active(@PathVariable Long id) { service.accessible(AuthContext.requireLogin(), id); return Result.success(service.active(id)); }
    @GetMapping("/student/active") public Result<?> mine() { return Result.success(service.activeForStudent(AuthContext.requireStudentId())); }
    @GetMapping("/student/check-ins") public Result<?> checkIns() { return Result.success(service.pendingCheckIns(AuthContext.requireStudentId())); }
    @GetMapping("/{id}") public Result<?> detail(@PathVariable String id) { return Result.success(service.requireSession(AuthContext.requireLogin(), id)); }
    @PostMapping("/{id}/heartbeat") public Result<?> heartbeat(@PathVariable String id) { service.heartbeat(AuthContext.requireTeacherId(),id); return Result.success(null); }
    @PostMapping("/{id}/end") public Result<?> end(@PathVariable String id) { service.end(AuthContext.requireTeacherId(),id); return Result.success(null); }
    @PostMapping("/{id}/signal") public Result<?> signal(@PathVariable String id,@RequestBody Signal r) { service.signal(AuthContext.requireLogin(),id,r.targetId(),r.kind(),r.payload()); return Result.success(null); }
    @GetMapping("/ice-config") public Result<?> ice() {
        AuthContext.requireUserId();
        var servers=new ArrayList<Map<String,Object>>();
        if(!stunUrl.isBlank()) servers.add(Map.of("urls",stunUrl));
        if(!turnUrl.isBlank()) servers.add(Map.of("urls",turnUrl,"username",turnUsername,"credential",turnCredential));
        return Result.success(Map.of("iceServers",servers));
    }
}

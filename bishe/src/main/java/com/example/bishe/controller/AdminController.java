package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.AccountBanRequest;
import com.example.bishe.dto.AdminDeregisterRequest;
import com.example.bishe.dto.AdminRegisterRequest;
import com.example.bishe.dto.ChatMessageUpdateRequest;
import com.example.bishe.dto.ChatMessageVO;
import com.example.bishe.dto.GroupBanRequest;
import com.example.bishe.dto.LoginRequest;
import com.example.bishe.dto.LoginResponse;
import com.example.bishe.dto.QuestionAuditRequest;
import com.example.bishe.dto.QuestionDeleteRequest;
import com.example.bishe.dto.QuestionView;
import com.example.bishe.dto.SpecialWordRequest;
import com.example.bishe.dto.ViolationHandleRequest;
import com.example.bishe.entity.Admin;
import com.example.bishe.entity.SpecialWord;
import com.example.bishe.service.AdminService;
import com.example.bishe.service.ChatService;
import com.example.bishe.service.GroupService;
import com.example.bishe.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员账号相关接口。
 */
@Tag(name = "管理员", description = "管理员登录、创建管理员、审核、聊天与群聊管理")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ChatService chatService;
    private final GroupService groupService;
    private final ModerationService moderationService;

    public AdminController(AdminService adminService, ChatService chatService,
                           GroupService groupService, ModerationService moderationService) {
        this.adminService = adminService;
        this.chatService = chatService;
        this.groupService = groupService;
        this.moderationService = moderationService;
    }

    /**
     * 查询是否允许首次初始化注册管理员。
     */
    @Operation(summary = "是否允许首次注册管理员")
    @GetMapping("/register/available")
    public Result<Map<String, Boolean>> registerAvailable() {
        Map<String, Boolean> data = new HashMap<>();
        data.put("available", adminService.canBootstrapRegister());
        return Result.success(data);
    }

    /**
     * 首次部署时初始化注册管理员。仅当系统中尚无任何管理员账号时允许调用。
     */
    @Operation(summary = "首次注册管理员")
    @PostMapping("/register")
    public Result<Admin> bootstrapRegister(@RequestBody AdminRegisterRequest request) {
        return Result.success("管理员注册成功", adminService.bootstrapRegister(request));
    }

    /**
     * 由已登录管理员创建新管理员。
     */
    @Operation(summary = "创建管理员(需已登录管理员)")
    @PostMapping("/create")
    public Result<Admin> createAdmin(@RequestBody AdminRegisterRequest request) {
        AuthContext.requireAdmin();
        return Result.success("管理员创建成功", adminService.createAdmin(request));
    }

    /**
     * 管理员登录, 返回 Token。
     */
    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success("登录成功", adminService.login(request));
    }

    /**
     * 管理员删除课程(级联删除课程下全部记录)。
     */
    @Operation(summary = "删除课程")
    @PostMapping("/course/delete")
    public Result<Void> deleteCourse(@RequestParam Long courseId) {
        AuthContext.requireAdmin();
        adminService.deleteCourse(courseId);
        return Result.success("课程已删除", null);
    }

    /**
     * 管理员注销账号(教师/学生), 并解绑其全部关联数据。
     */
    @Operation(summary = "注销教师/学生账号")
    @PostMapping("/account/deregister")
    public Result<Void> deregister(@RequestBody AdminDeregisterRequest request) {
        AuthContext.requireAdmin();
        adminService.deregisterAccount(request);
        return Result.success("账号已注销", null);
    }

    @Operation(summary = "分页查询学生账号(昵称/ID)")
    @GetMapping("/account/students")
    public Result<Map<String, Object>> listStudents(@RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin();
        return Result.success(adminService.listStudents(keyword, page, size));
    }

    @Operation(summary = "分页查询教师账号(姓名/ID)")
    @GetMapping("/account/teachers")
    public Result<Map<String, Object>> listTeachers(@RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin();
        return Result.success(adminService.listTeachers(keyword, page, size));
    }

    @Operation(summary = "分页查询全部课程(可按课程号筛选)")
    @GetMapping("/course/list")
    public Result<Map<String, Object>> listCourses(@RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin();
        return Result.success(adminService.listCourses(keyword, page, size));
    }

    @Operation(summary = "待审核题库列表")
    @GetMapping("/question/pending")
    public Result<List<QuestionView>> pendingQuestions() {
        AuthContext.requireAdmin();
        return Result.success(adminService.listPendingAuditQuestions());
    }

    @Operation(summary = "审核题库题目")
    @PostMapping("/question/audit")
    public Result<Void> auditQuestion(@RequestBody QuestionAuditRequest request) {
        AuthContext.requireAdmin();
        adminService.auditQuestion(request);
        return Result.success("审核完成", null);
    }

    @Operation(summary = "删除题库题目")
    @PostMapping("/question/delete")
    public Result<Void> deleteQuestion(@RequestBody QuestionDeleteRequest request) {
        AuthContext.requireAdmin();
        adminService.deleteQuestion(request);
        return Result.success("题目已删除", null);
    }

    @Operation(summary = "分页查看全部聊天记录(可筛选私聊/群聊)")
    @GetMapping("/chat/records")
    public Result<Map<String, Object>> chatRecords(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(required = false) Integer isPrivate) {
        AuthContext.requireAdmin();
        return Result.success(chatService.listAllForAdmin(page, size, isPrivate));
    }

    @Operation(summary = "查看某群聊天记录")
    @GetMapping("/chat/group")
    public Result<List<ChatMessageVO>> groupChatRecords(@RequestParam Long groupId,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        AuthContext.requireAdmin();
        return Result.success(chatService.listGroupForAdmin(groupId, page, size));
    }

    @Operation(summary = "查看双方私聊记录")
    @GetMapping("/chat/private")
    public Result<List<ChatMessageVO>> privateChatRecords(@RequestParam Long userA,
                                                           @RequestParam Long userB,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "50") int size) {
        AuthContext.requireAdmin();
        return Result.success(chatService.listPrivateForAdmin(userA, userB, page, size));
    }

    @Operation(summary = "修改聊天记录内容")
    @PostMapping("/chat/update")
    public Result<Void> updateChat(@RequestBody ChatMessageUpdateRequest request) {
        AuthContext.requireAdmin();
        chatService.adminUpdateContent(request);
        return Result.success("消息已修改", null);
    }

    @Operation(summary = "软删除聊天记录")
    @PostMapping("/chat/delete")
    public Result<Void> deleteChat(@RequestParam Long messageId) {
        AuthContext.requireAdmin();
        chatService.adminSoftDelete(messageId);
        return Result.success("消息已软删除", null);
    }

    @Operation(summary = "特殊词列表")
    @GetMapping("/special-word/list")
    public Result<List<SpecialWord>> specialWordList() {
        AuthContext.requireAdmin();
        return Result.success(moderationService.listWords());
    }

    @Operation(summary = "新增特殊词")
    @PostMapping("/special-word/add")
    public Result<SpecialWord> addSpecialWord(@RequestBody SpecialWordRequest request) {
        String adminId = AuthContext.requireAdminId();
        return Result.success("特殊词已添加", moderationService.addWord(request, adminId));
    }

    @Operation(summary = "启用/停用特殊词")
    @PostMapping("/special-word/enable")
    public Result<Void> enableSpecialWord(@RequestParam Long wordId, @RequestParam int enabled) {
        AuthContext.requireAdmin();
        moderationService.setWordEnabled(wordId, enabled);
        return Result.success("已更新", null);
    }

    @Operation(summary = "删除特殊词")
    @PostMapping("/special-word/delete")
    public Result<Void> deleteSpecialWord(@RequestParam Long wordId) {
        AuthContext.requireAdmin();
        moderationService.deleteWord(wordId);
        return Result.success("特殊词已删除", null);
    }

    @Operation(summary = "特殊词命中记录")
    @GetMapping("/special-word/violations")
    public Result<Map<String, Object>> specialWordViolations(@RequestParam(required = false) Integer status,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        AuthContext.requireAdmin();
        return Result.success(moderationService.listViolations(status, page, size));
    }

    @Operation(summary = "处理特殊词命中: IGNORE/WARN/BAN")
    @PostMapping("/special-word/handle")
    public Result<Void> handleSpecialWord(@RequestBody ViolationHandleRequest request) {
        String adminId = AuthContext.requireAdminId();
        moderationService.handleViolation(request, adminId);
        return Result.success("处理完成", null);
    }

    @Operation(summary = "封禁学生/教师账号")
    @PostMapping("/account/ban")
    public Result<Void> banAccount(@RequestBody AccountBanRequest request) {
        String adminId = AuthContext.requireAdminId();
        moderationService.banAccount(request, adminId);
        return Result.success("账号已封禁", null);
    }

    @Operation(summary = "解禁学生/教师账号")
    @PostMapping("/account/unban")
    public Result<Void> unbanAccount(@RequestParam String accountType, @RequestParam Long accountId) {
        AuthContext.requireAdmin();
        moderationService.unbanAccount(accountType, accountId);
        return Result.success("账号已解禁", null);
    }

    @Operation(summary = "软删除学生/教师账号")
    @PostMapping("/account/soft-delete")
    public Result<Void> softDeleteAccount(@RequestParam String accountType,
                                           @RequestParam Long accountId,
                                           @RequestParam(required = false) String reason) {
        String adminId = AuthContext.requireAdminId();
        moderationService.softDeleteAccount(accountType, accountId, reason, adminId);
        return Result.success("账号已软删除", null);
    }

    @Operation(summary = "分页查看全部群聊")
    @GetMapping("/group/list")
    public Result<Map<String, Object>> listGroups(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        AuthContext.requireAdmin();
        return Result.success(groupService.listAllForAdmin(page, size));
    }

    @Operation(summary = "软删除群聊")
    @PostMapping("/group/soft-delete")
    public Result<Void> softDeleteGroup(@RequestParam Long groupId) {
        AuthContext.requireAdmin();
        groupService.softDeleteByAdmin(groupId);
        return Result.success("群聊已软删除", null);
    }

    @Operation(summary = "封禁群聊")
    @PostMapping("/group/ban")
    public Result<Void> banGroup(@RequestBody GroupBanRequest request) {
        String adminId = AuthContext.requireAdminId();
        if (request == null || request.getGroupId() == null) {
            return Result.error("群号不能为空");
        }
        groupService.banByAdmin(request.getGroupId(), request.getBanReason(), adminId);
        return Result.success("群聊已封禁", null);
    }

    @Operation(summary = "解除群聊封禁")
    @PostMapping("/group/unban")
    public Result<Void> unbanGroup(@RequestParam Long groupId) {
        AuthContext.requireAdmin();
        groupService.unbanByAdmin(groupId);
        return Result.success("群聊已解除封禁", null);
    }
}

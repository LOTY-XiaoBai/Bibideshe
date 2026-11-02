package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.GroupAppointAdminRequest;
import com.example.bishe.dto.GroupCreateRequest;
import com.example.bishe.dto.GroupDetailVO;
import com.example.bishe.dto.GroupIdRequest;
import com.example.bishe.dto.GroupMemberVO;
import com.example.bishe.dto.GroupUpdateRequest;
import com.example.bishe.entity.GroupChat;
import com.example.bishe.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 群聊相关接口(学生/教师共用)。
 */
@Tag(name = "群聊", description = "群聊创建、加入、管理与成员")
@RestController
@RequestMapping("/api/group")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    /** 创建群聊(创建者成为群主/高级管理员) */
    @Operation(summary = "创建群聊")
    @PostMapping("/create")
    public Result<GroupChat> create(@RequestBody GroupCreateRequest request) {
        Long userId = AuthContext.requireUserId();
        return Result.success("群聊创建成功", groupService.createGroup(userId, request));
    }

    /** 按群号或群名搜索 */
    @Operation(summary = "按群号或群名搜索")
    @GetMapping("/search")
    public Result<List<GroupChat>> search(@RequestParam String keyword) {
        AuthContext.requireUserId();
        return Result.success(groupService.search(keyword));
    }

    /** 我加入的群聊 */
    @Operation(summary = "我加入的群聊")
    @GetMapping("/mine")
    public Result<List<GroupDetailVO>> mine() {
        Long userId = AuthContext.requireUserId();
        return Result.success(groupService.myGroups(userId));
    }

    /** 群聊详情 */
    @Operation(summary = "群聊详情")
    @GetMapping("/{groupId}")
    public Result<GroupDetailVO> detail(@PathVariable Long groupId) {
        Long userId = AuthContext.requireUserId();
        return Result.success(groupService.getDetail(userId, groupId));
    }

    /** 群成员列表(含管理员等级) */
    @Operation(summary = "群成员列表")
    @GetMapping("/{groupId}/members")
    public Result<List<GroupMemberVO>> members(@PathVariable Long groupId) {
        Long userId = AuthContext.requireUserId();
        return Result.success(groupService.listMembers(userId, groupId));
    }

    /** 加入群聊 */
    @Operation(summary = "加入群聊")
    @PostMapping("/join")
    public Result<Void> join(@RequestBody GroupIdRequest request) {
        Long userId = AuthContext.requireUserId();
        groupService.join(userId, request.getGroupId());
        return Result.success("已加入群聊", null);
    }

    /** 退出群聊(群主不可退出, 需解散) */
    @Operation(summary = "退出群聊")
    @PostMapping("/leave")
    public Result<Void> leave(@RequestBody GroupIdRequest request) {
        Long userId = AuthContext.requireUserId();
        groupService.leave(userId, request.getGroupId());
        return Result.success("已退出群聊", null);
    }

    /** 解散群聊(仅群主) */
    @Operation(summary = "解散群聊")
    @PostMapping("/dissolve")
    public Result<Void> dissolve(@RequestBody GroupIdRequest request) {
        Long userId = AuthContext.requireUserId();
        groupService.dissolve(userId, request.getGroupId());
        return Result.success("群聊已解散", null);
    }

    /** 修改群名/群头像/群简介(群主或管理员) */
    @Operation(summary = "修改群名/群头像/群简介")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody GroupUpdateRequest request) {
        Long userId = AuthContext.requireUserId();
        groupService.updateInfo(userId, request);
        return Result.success("群资料已更新", null);
    }

    /** 群主任命普通管理员 */
    @Operation(summary = "群主任命普通管理员")
    @PostMapping("/appoint-admin")
    public Result<Void> appointAdmin(@RequestBody GroupAppointAdminRequest request) {
        Long userId = AuthContext.requireUserId();
        groupService.appointAdmin(userId, request);
        return Result.success("已任命管理员", null);
    }

    /** 群主撤销普通管理员 */
    @Operation(summary = "群主撤销普通管理员")
    @PostMapping("/remove-admin")
    public Result<Void> removeAdmin(@RequestBody GroupAppointAdminRequest request) {
        Long userId = AuthContext.requireUserId();
        groupService.removeAdmin(userId, request);
        return Result.success("已撤销管理员", null);
    }

    /** 群主或普通管理员移出成员（服务端校验权限层级） */
    @Operation(summary = "群管理员移出成员")
    @PostMapping("/remove-member")
    public Result<Void> removeMember(@RequestBody GroupAppointAdminRequest request) {
        Long userId = AuthContext.requireUserId();
        groupService.removeMember(userId, request);
        return Result.success("已将成员移出群聊", null);
    }
}

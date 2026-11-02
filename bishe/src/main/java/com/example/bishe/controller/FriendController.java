package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.FriendDeleteRequest;
import com.example.bishe.dto.FriendRequestCreateRequest;
import com.example.bishe.dto.FriendRequestHandleRequest;
import com.example.bishe.dto.FriendRequestVO;
import com.example.bishe.dto.ScorePkVO;
import com.example.bishe.dto.UserProfileVO;
import com.example.bishe.entity.FriendRequest;
import com.example.bishe.service.FriendService;
import com.example.bishe.service.ScoreCompareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 好友相关接口(学生/教师共用)。
 */
@Tag(name = "好友", description = "好友申请、列表、删除与成绩PK")
@RestController
@RequestMapping("/api/friend")
public class FriendController {

    private final FriendService friendService;
    private final ScoreCompareService scoreCompareService;

    public FriendController(FriendService friendService, ScoreCompareService scoreCompareService) {
        this.friendService = friendService;
        this.scoreCompareService = scoreCompareService;
    }

    @Operation(summary = "按用户ID搜索")
    @GetMapping("/search")
    public Result<UserProfileVO> search(@RequestParam Long userId) {
        AuthContext.requireUserId();
        return Result.success(friendService.searchUser(userId));
    }

    @Operation(summary = "按昵称/姓名搜索(同名全部列出)")
    @GetMapping("/search/nickname")
    public Result<List<UserProfileVO>> searchByNickname(@RequestParam String nickname) {
        AuthContext.requireUserId();
        return Result.success(friendService.searchByNickname(nickname));
    }

    @Operation(summary = "发起好友申请")
    @PostMapping("/request")
    public Result<FriendRequest> sendRequest(@RequestBody FriendRequestCreateRequest request) {
        Long userId = AuthContext.requireUserId();
        return Result.success("好友申请已发送", friendService.sendRequest(userId, request.getReceiverId()));
    }

    @Operation(summary = "收到的待处理申请")
    @GetMapping("/requests/received")
    public Result<List<FriendRequestVO>> received() {
        Long userId = AuthContext.requireUserId();
        return Result.success(friendService.listReceivedPending(userId));
    }

    @Operation(summary = "发出的申请")
    @GetMapping("/requests/sent")
    public Result<List<FriendRequestVO>> sent() {
        Long userId = AuthContext.requireUserId();
        return Result.success(friendService.listSent(userId));
    }

    @Operation(summary = "同意好友申请")
    @PostMapping("/request/accept")
    public Result<Void> accept(@RequestBody FriendRequestHandleRequest request) {
        Long userId = AuthContext.requireUserId();
        friendService.acceptRequest(userId, request.getRequestId());
        return Result.success("已同意好友申请", null);
    }

    @Operation(summary = "拒绝好友申请")
    @PostMapping("/request/reject")
    public Result<Void> reject(@RequestBody FriendRequestHandleRequest request) {
        Long userId = AuthContext.requireUserId();
        friendService.rejectRequest(userId, request.getRequestId());
        return Result.success("已拒绝好友申请", null);
    }

    @Operation(summary = "好友列表")
    @GetMapping("/list")
    public Result<List<UserProfileVO>> list() {
        Long userId = AuthContext.requireUserId();
        return Result.success(friendService.listFriends(userId));
    }

    @Operation(summary = "删除好友")
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody FriendDeleteRequest request) {
        Long userId = AuthContext.requireUserId();
        friendService.deleteFriend(userId, request.getFriendId());
        return Result.success("已删除好友", null);
    }

    /**
     * 与好友成绩 PK。
     * 不传 courseId: 对比综合评分; 传入 courseId: 对比该课程成绩(双方须均为该班学生)。
     */
    @Operation(summary = "与好友成绩PK")
    @GetMapping("/pk")
    public Result<ScorePkVO> pk(@RequestParam Long friendId,
                                 @RequestParam(required = false) Long courseId) {
        Long userId = AuthContext.requireUserId();
        return Result.success(scoreCompareService.pkWithFriend(userId, friendId, courseId));
    }
}

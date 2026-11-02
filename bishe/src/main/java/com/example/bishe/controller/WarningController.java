package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.UserWarningVO;
import com.example.bishe.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生/教师端警告接口。
 */
@Tag(name = "用户警告", description = "未读警告弹窗与不再显示")
@RestController
@RequestMapping("/api/warning")
public class WarningController {

    private final ModerationService moderationService;

    public WarningController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Operation(summary = "未读警告列表(弹窗用)")
    @GetMapping("/unread")
    public Result<List<UserWarningVO>> unread() {
        Long userId = AuthContext.requireUserId();
        return Result.success(moderationService.listUnreadWarnings(userId));
    }

    @Operation(summary = "可见警告列表(系统通知会话)")
    @GetMapping("/list")
    public Result<List<UserWarningVO>> list() {
        Long userId = AuthContext.requireUserId();
        return Result.success(moderationService.listVisibleWarnings(userId));
    }

    @Operation(summary = "标记警告已读")
    @PostMapping("/read")
    public Result<Void> markRead(@RequestParam(required = false) Long warningId) {
        Long userId = AuthContext.requireUserId();
        moderationService.markWarningRead(userId, warningId);
        return Result.success("已标记已读", null);
    }

    @Operation(summary = "不再显示该警告(私聊侧隐藏)")
    @PostMapping("/dismiss")
    public Result<Void> dismiss(@RequestParam Long warningId) {
        Long userId = AuthContext.requireUserId();
        moderationService.dismissWarning(userId, warningId);
        return Result.success("已不再显示", null);
    }
}

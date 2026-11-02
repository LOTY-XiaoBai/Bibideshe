package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.Result;
import com.example.bishe.dto.ChatMessageUpdateRequest;
import com.example.bishe.dto.ChatMessageVO;
import com.example.bishe.dto.ChatRecallRequest;
import com.example.bishe.dto.ChatSendRequest;
import com.example.bishe.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 私聊/群聊消息接口(学生/教师共用)。
 * REST 接口仍可用; 实时推送请连接 WebSocket(/ws) 并订阅 /user/queue/chat 与 /topic/group/{id}。
 */
@Tag(name = "聊天", description = "私聊/群聊 REST 接口; 实时消息见 WebSocket")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "发送私聊消息")
    @PostMapping("/private/send")
    public Result<ChatMessageVO> sendPrivate(@RequestBody ChatSendRequest request) {
        Long userId = AuthContext.requireUserId();
        return Result.success("发送成功", chatService.sendPrivate(userId, request));
    }

    @Operation(summary = "私聊历史")
    @GetMapping("/private/{friendId}")
    public Result<List<ChatMessageVO>> privateHistory(@PathVariable Long friendId) {
        Long userId = AuthContext.requireUserId();
        return Result.success(chatService.privateHistory(userId, friendId));
    }

    @Operation(summary = "发送群聊消息")
    @PostMapping("/group/send")
    public Result<ChatMessageVO> sendGroup(@RequestBody ChatSendRequest request) {
        Long userId = AuthContext.requireUserId();
        return Result.success("发送成功", chatService.sendGroup(userId, request));
    }

    @Operation(summary = "群聊历史")
    @GetMapping("/group/{groupId}")
    public Result<List<ChatMessageVO>> groupHistory(@PathVariable Long groupId) {
        Long userId = AuthContext.requireUserId();
        return Result.success(chatService.groupHistory(userId, groupId));
    }

    @Operation(summary = "撤回消息")
    @PostMapping("/recall")
    public Result<Void> recall(@RequestBody ChatRecallRequest request) {
        Long userId = AuthContext.requireUserId();
        chatService.recall(userId, request.getMessageId());
        return Result.success("消息已撤回", null);
    }

    @Operation(summary = "群管理员修改消息")
    @PostMapping("/group/update")
    public Result<Void> updateByAdmin(@RequestBody ChatMessageUpdateRequest request) {
        Long userId = AuthContext.requireUserId();
        chatService.updateByGroupAdmin(userId, request);
        return Result.success("消息已修改", null);
    }
}

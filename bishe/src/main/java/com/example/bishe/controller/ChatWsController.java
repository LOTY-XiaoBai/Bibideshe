package com.example.bishe.controller;

import com.example.bishe.common.AuthUser;
import com.example.bishe.common.BusinessException;
import com.example.bishe.common.UserRole;
import com.example.bishe.config.WebSocketConfig;
import com.example.bishe.dto.ChatMessageVO;
import com.example.bishe.dto.ChatSendRequest;
import com.example.bishe.service.ChatService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket 聊天入口(STOMP)。
 * 客户端发送到 /app/chat.private 或 /app/chat.group, 服务端经 ChatService 落库并推送。
 */
@Controller
public class ChatWsController {

    private final ChatService chatService;

    public ChatWsController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat.private")
    @SendToUser("/queue/chat-ack")
    public ChatMessageVO sendPrivate(@Payload ChatSendRequest request, Principal principal) {
        Long userId = requireNumericUser(principal);
        return chatService.sendPrivate(userId, request);
    }

    @MessageMapping("/chat.group")
    @SendToUser("/queue/chat-ack")
    public ChatMessageVO sendGroup(@Payload ChatSendRequest request, Principal principal) {
        Long userId = requireNumericUser(principal);
        return chatService.sendGroup(userId, request);
    }

    private Long requireNumericUser(Principal principal) {
        if (!(principal instanceof WebSocketConfig.StompPrincipal stompPrincipal)) {
            throw new BusinessException("WebSocket 未鉴权");
        }
        AuthUser user = stompPrincipal.getAuthUser();
        if (UserRole.ADMIN.equals(user.getRole()) || user.getUserId() == null) {
            throw new BusinessException("当前账号不支持聊天");
        }
        return user.getUserId();
    }
}

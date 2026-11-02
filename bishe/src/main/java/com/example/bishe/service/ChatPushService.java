package com.example.bishe.service;

import com.example.bishe.dto.BanInfo;
import com.example.bishe.dto.ChatMessageVO;
import com.example.bishe.dto.UserWarningVO;
import com.example.bishe.entity.GroupUser;
import com.example.bishe.mapper.GroupUserMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天实时推送: 私聊推给双方, 群聊推给群成员; 撤回/修改同步通知。
 * 另支持封禁强制下线、警告弹窗推送。
 */
@Service
public class ChatPushService {

    private final SimpMessagingTemplate messagingTemplate;
    private final GroupUserMapper groupUserMapper;

    public ChatPushService(SimpMessagingTemplate messagingTemplate, GroupUserMapper groupUserMapper) {
        this.messagingTemplate = messagingTemplate;
        this.groupUserMapper = groupUserMapper;
    }

    public void pushNewMessage(ChatMessageVO message) {
        if (message == null) {
            return;
        }
        Object payload = event("NEW_MESSAGE", message);
        if (message.getIsPrivate() != null && message.getIsPrivate() == 1) {
            pushToUser(message.getSenderId(), payload);
            pushToUser(message.getReceiverId(), payload);
        } else if (message.getGroupId() != null && message.getGroupId() > 0) {
            messagingTemplate.convertAndSend("/topic/group/" + message.getGroupId(), payload);
            List<GroupUser> members = groupUserMapper.findByGroupId(message.getGroupId());
            for (GroupUser member : members) {
                pushToUser(member.getUserId(), payload);
            }
        }
    }

    public void pushRecall(Long messageId, Integer isPrivate, Long receiverId, Long groupId, Long operatorId) {
        Map<String, Object> body = new HashMap<>();
        body.put("messageId", messageId);
        body.put("operatorId", operatorId);
        Object payload = event("RECALL", body);
        if (isPrivate != null && isPrivate == 1) {
            pushToUser(operatorId, payload);
            pushToUser(receiverId, payload);
        } else if (groupId != null && groupId > 0) {
            messagingTemplate.convertAndSend("/topic/group/" + groupId, payload);
        }
    }

    public void pushContentUpdate(ChatMessageVO message) {
        if (message == null) {
            return;
        }
        Object payload = event("UPDATE", message);
        if (message.getIsPrivate() != null && message.getIsPrivate() == 1) {
            pushToUser(message.getSenderId(), payload);
            pushToUser(message.getReceiverId(), payload);
        } else if (message.getGroupId() != null && message.getGroupId() > 0) {
            messagingTemplate.convertAndSend("/topic/group/" + message.getGroupId(), payload);
        }
    }

    /** 管理员封禁后强制在线用户下线 */
    public void pushForceLogout(Long userId, BanInfo banInfo) {
        pushToUser(userId, event("FORCE_LOGOUT", banInfo));
    }

    /** 在线用户收到可关闭的警告弹窗 */
    public void pushUserWarning(Long userId, UserWarningVO warning) {
        pushToUser(userId, event("USER_WARNING", warning));
    }

    private void pushToUser(Long userId, Object payload) {
        if (userId == null || userId <= 0) {
            return;
        }
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/chat", payload);
    }

    private Object event(String type, Object data) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("data", data);
        return map;
    }
}

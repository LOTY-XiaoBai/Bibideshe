package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.dto.ChatMessageUpdateRequest;
import com.example.bishe.dto.ChatMessageVO;
import com.example.bishe.dto.ChatSendRequest;
import com.example.bishe.dto.UserProfileVO;
import com.example.bishe.entity.ChatRecord;
import com.example.bishe.mapper.ChatRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 私聊/群聊消息服务。消息软删除后用户侧不可见, 管理员仍可查看与修改。
 */
@Service
public class ChatService {

    public static final int MSG_TEXT = 0;
    public static final int MSG_IMAGE = 1;
    public static final int MSG_SYSTEM_WARN = 2;

    private final ChatRecordMapper chatRecordMapper;
    private final FriendService friendService;
    private final GroupService groupService;
    private final SocialUserService socialUserService;
    private final ChatPushService chatPushService;
    private final ModerationService moderationService;

    public ChatService(ChatRecordMapper chatRecordMapper,
                       FriendService friendService,
                       GroupService groupService,
                       SocialUserService socialUserService,
                       ChatPushService chatPushService,
                       ModerationService moderationService) {
        this.chatRecordMapper = chatRecordMapper;
        this.friendService = friendService;
        this.groupService = groupService;
        this.socialUserService = socialUserService;
        this.chatPushService = chatPushService;
        this.moderationService = moderationService;
    }

    @Transactional
    public ChatMessageVO sendPrivate(Long senderId, ChatSendRequest request) {
        moderationService.assertAccountActive(senderId);
        if (request.getReceiverId() == null) {
            throw new BusinessException("私聊接收人不能为空");
        }
        moderationService.assertAccountActive(request.getReceiverId());
        validateContent(request);
        friendService.requireFriendship(senderId, request.getReceiverId());

        Long referenceId = normalizeReferenceId(request.getReferenceId());
        if (referenceId > 0) {
            ChatRecord ref = chatRecordMapper.findById(referenceId);
            if (ref == null || ref.getIsPrivate() == null || ref.getIsPrivate() != 1) {
                throw new BusinessException("引用的消息不存在或不属于私聊");
            }
            boolean related = (senderId.equals(ref.getSenderId()) || senderId.equals(ref.getReceiverId()))
                    && (request.getReceiverId().equals(ref.getSenderId()) || request.getReceiverId().equals(ref.getReceiverId()));
            if (!related) {
                throw new BusinessException("只能引用当前私聊会话中的消息");
            }
        }

        ChatRecord record = buildBaseRecord(senderId, request);
        record.setReceiverId(request.getReceiverId());
        record.setIsPrivate(1);
        record.setGroupId(0L);
        record.setReferenceId(referenceId);
        chatRecordMapper.insert(record);
        ChatRecord saved = chatRecordMapper.findById(record.getMessageId());
        moderationService.scanAndRecord(saved);
        ChatMessageVO vo = toVO(saved, true);
        chatPushService.pushNewMessage(vo);
        return vo;
    }

    @Transactional
    public ChatMessageVO sendGroup(Long senderId, ChatSendRequest request) {
        moderationService.assertAccountActive(senderId);
        if (request.getGroupId() == null) {
            throw new BusinessException("群号不能为空");
        }
        validateContent(request);
        groupService.requireActiveGroup(request.getGroupId());
        groupService.requireMember(request.getGroupId(), senderId);

        Long referenceId = normalizeReferenceId(request.getReferenceId());
        if (referenceId > 0) {
            ChatRecord ref = chatRecordMapper.findById(referenceId);
            if (ref == null || ref.getIsPrivate() == null || ref.getIsPrivate() != 0
                    || !request.getGroupId().equals(ref.getGroupId())) {
                throw new BusinessException("只能引用本群中的消息");
            }
        }

        ChatRecord record = buildBaseRecord(senderId, request);
        record.setReceiverId(0L);
        record.setIsPrivate(0);
        record.setGroupId(request.getGroupId());
        record.setReferenceId(referenceId);
        chatRecordMapper.insert(record);
        ChatRecord saved = chatRecordMapper.findById(record.getMessageId());
        moderationService.scanAndRecord(saved);
        ChatMessageVO vo = toVO(saved, true);
        chatPushService.pushNewMessage(vo);
        return vo;
    }

    public List<ChatMessageVO> privateHistory(Long userId, Long friendId) {
        if (friendId == null) {
            throw new BusinessException("好友ID不能为空");
        }
        // 系统通知会话: friendId = 0
        if (friendId == 0L) {
            return toVOList(chatRecordMapper.findSystemNotifyHistory(userId), false);
        }
        friendService.requireFriendship(userId, friendId);
        return toVOList(chatRecordMapper.findPrivateHistory(userId, friendId), false);
    }

    public List<ChatMessageVO> groupHistory(Long userId, Long groupId) {
        groupService.requireActiveGroup(groupId);
        groupService.requireMember(groupId, userId);
        return toVOList(chatRecordMapper.findGroupHistory(groupId), false);
    }

    /**
     * 撤回(软删除): 发送者本人, 或群管理员可撤回群消息。
     */
    @Transactional
    public void recall(Long userId, Long messageId) {
        ChatRecord record = requireMessage(messageId);
        if (record.getIsDeleted() != null && record.getIsDeleted() == 1) {
            throw new BusinessException("消息已撤回");
        }
        boolean isSender = userId.equals(record.getSenderId());
        boolean isGroupAdmin = record.getIsPrivate() != null && record.getIsPrivate() == 0
                && groupService.getAdminLevel(record.getGroupId(), userId) != null;
        if (!isSender && !isGroupAdmin) {
            throw new BusinessException("无权撤回该消息");
        }
        if (isGroupAdmin && record.getIsPrivate() == 0) {
            groupService.requireActiveGroup(record.getGroupId());
            groupService.requireMember(record.getGroupId(), userId);
        }
        chatRecordMapper.softDelete(messageId);
        chatPushService.pushRecall(messageId, record.getIsPrivate(), record.getReceiverId(),
                record.getGroupId(), userId);
    }

    /**
     * 群管理员修改群消息内容(非删除)。
     */
    @Transactional
    public void updateByGroupAdmin(Long userId, ChatMessageUpdateRequest request) {
        if (request.getMessageId() == null) {
            throw new BusinessException("消息ID不能为空");
        }
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new BusinessException("消息内容不能为空");
        }
        String content = request.getContent().trim();
        if (content.length() > 2000) {
            throw new BusinessException("消息内容不能超过2000个字符");
        }
        ChatRecord record = requireMessage(request.getMessageId());
        if (record.getIsPrivate() == null || record.getIsPrivate() != 0) {
            throw new BusinessException("仅群聊消息可由群管理员修改");
        }
        if (record.getIsDeleted() != null && record.getIsDeleted() == 1) {
            throw new BusinessException("已撤回的消息不能修改");
        }
        if (record.getMessageType() == null || record.getMessageType() != 0) {
            throw new BusinessException("群管理员仅可修改文本消息");
        }
        groupService.requireActiveGroup(record.getGroupId());
        groupService.requireAdmin(record.getGroupId(), userId);
        chatRecordMapper.updateContent(request.getMessageId(), content);
        ChatMessageVO vo = toVO(chatRecordMapper.findById(request.getMessageId()), true);
        chatPushService.pushContentUpdate(vo);
    }

    // ---------- 平台管理员 ----------

    public Map<String, Object> listAllForAdmin(int page, int size, Integer isPrivate) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 20;
        }
        int offset = (page - 1) * size;
        List<ChatMessageVO> records = toVOList(chatRecordMapper.findAllForAdmin(isPrivate, offset, size), true);
        Map<String, Object> result = new HashMap<>();
        result.put("total", chatRecordMapper.countAll(isPrivate));
        result.put("page", page);
        result.put("size", size);
        result.put("list", records);
        result.put("records", records);
        return result;
    }

    public List<ChatMessageVO> listGroupForAdmin(Long groupId, int page, int size) {
        if (groupId == null) {
            throw new BusinessException("群号不能为空");
        }
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 20;
        }
        int offset = (page - 1) * size;
        return toVOList(chatRecordMapper.findByGroupForAdmin(groupId, offset, size), true);
    }

    public List<ChatMessageVO> listPrivateForAdmin(Long userA, Long userB, int page, int size) {
        if (userA == null || userB == null) {
            throw new BusinessException("私聊双方用户ID不能为空");
        }
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 50;
        }
        int offset = (page - 1) * size;
        return toVOList(chatRecordMapper.findPrivateForAdmin(userA, userB, offset, size), true);
    }

    @Transactional
    public void adminUpdateContent(ChatMessageUpdateRequest request) {
        if (request.getMessageId() == null) {
            throw new BusinessException("消息ID不能为空");
        }
        if (request.getContent() == null) {
            throw new BusinessException("消息内容不能为空");
        }
        requireMessage(request.getMessageId());
        chatRecordMapper.updateContent(request.getMessageId(), request.getContent());
        ChatMessageVO vo = toVO(chatRecordMapper.findById(request.getMessageId()), true);
        chatPushService.pushContentUpdate(vo);
    }

    @Transactional
    public void adminSoftDelete(Long messageId) {
        ChatRecord record = requireMessage(messageId);
        chatRecordMapper.softDelete(messageId);
        chatPushService.pushRecall(messageId, record.getIsPrivate(), record.getReceiverId(),
                record.getGroupId(), null);
    }

    // ---------- 内部 ----------

    private void validateContent(ChatSendRequest request) {
        if (request.getContent() == null || request.getContent().isEmpty()) {
            throw new BusinessException("消息内容不能为空");
        }
        Integer type = request.getMessageType() == null ? MSG_TEXT : request.getMessageType();
        if (type != MSG_TEXT && type != MSG_IMAGE) {
            throw new BusinessException("不支持的消息类型");
        }
        request.setMessageType(type);
    }

    private ChatRecord buildBaseRecord(Long senderId, ChatSendRequest request) {
        ChatRecord record = new ChatRecord();
        record.setSenderId(senderId);
        record.setContent(request.getContent());
        record.setMessageType(request.getMessageType());
        record.setIsDeleted(0);
        return record;
    }

    private Long normalizeReferenceId(Long referenceId) {
        return referenceId == null ? 0L : referenceId;
    }

    private ChatRecord requireMessage(Long messageId) {
        if (messageId == null) {
            throw new BusinessException("消息ID不能为空");
        }
        ChatRecord record = chatRecordMapper.findById(messageId);
        if (record == null) {
            throw new BusinessException("消息不存在");
        }
        return record;
    }

    private List<ChatMessageVO> toVOList(List<ChatRecord> records, boolean includeDeleted) {
        List<ChatRecord> visible = new ArrayList<>();
        for (ChatRecord record : records) {
            if (!includeDeleted && record.getIsDeleted() != null && record.getIsDeleted() == 1) {
                continue;
            }
            visible.add(record);
        }
        if (visible.isEmpty()) {
            return List.of();
        }

        List<Long> senderIds = new ArrayList<>();
        List<Long> referenceIds = new ArrayList<>();
        for (ChatRecord record : visible) {
            if (record.getSenderId() != null) {
                senderIds.add(record.getSenderId());
            }
            if (record.getReferenceId() != null && record.getReferenceId() > 0) {
                referenceIds.add(record.getReferenceId());
            }
        }
        Map<Long, UserProfileVO> profileMap = socialUserService.findProfiles(senderIds);
        Map<Long, ChatRecord> referenceMap = new HashMap<>();
        if (!referenceIds.isEmpty()) {
            // 引用消息通常较少, 逐条加载; 同时收集其发送者资料
            List<Long> refSenderIds = new ArrayList<>();
            for (Long refId : referenceIds.stream().distinct().collect(java.util.stream.Collectors.toList())) {
                ChatRecord ref = chatRecordMapper.findById(refId);
                if (ref != null) {
                    referenceMap.put(refId, ref);
                    if (ref.getSenderId() != null) {
                        refSenderIds.add(ref.getSenderId());
                    }
                }
            }
            if (!refSenderIds.isEmpty()) {
                profileMap.putAll(socialUserService.findProfiles(refSenderIds));
            }
        }

        List<ChatMessageVO> result = new ArrayList<>();
        for (ChatRecord record : visible) {
            result.add(toVO(record, true, profileMap, referenceMap));
        }
        return result;
    }

    private ChatMessageVO toVO(ChatRecord record, boolean fillReference) {
        return toVO(record, fillReference, null, null);
    }

    private ChatMessageVO toVO(ChatRecord record, boolean fillReference,
                               Map<Long, UserProfileVO> profileMap,
                               Map<Long, ChatRecord> referenceMap) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setMessageId(record.getMessageId());
        vo.setSendTime(record.getSendTime());
        vo.setSenderId(record.getSenderId());
        if (profileMap != null) {
            vo.setSender(profileMap.get(record.getSenderId()));
        } else {
            vo.setSender(socialUserService.findProfile(record.getSenderId()));
        }
        vo.setReceiverId(record.getReceiverId());
        vo.setIsPrivate(record.getIsPrivate());
        vo.setGroupId(record.getGroupId());
        vo.setContent(record.getContent());
        vo.setMessageType(record.getMessageType());
        vo.setReferenceId(record.getReferenceId());
        vo.setIsDeleted(record.getIsDeleted());
        if (fillReference && record.getReferenceId() != null && record.getReferenceId() > 0) {
            ChatRecord ref = referenceMap != null
                    ? referenceMap.get(record.getReferenceId())
                    : chatRecordMapper.findById(record.getReferenceId());
            if (ref != null) {
                vo.setReferencedMessage(toVO(ref, false, profileMap, null));
            }
        }
        return vo;
    }
}

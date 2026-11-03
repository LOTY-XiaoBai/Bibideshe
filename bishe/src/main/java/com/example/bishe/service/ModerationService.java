package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.dto.AccountBanRequest;
import com.example.bishe.dto.BanInfo;
import com.example.bishe.dto.ChatViolationVO;
import com.example.bishe.dto.SpecialWordRequest;
import com.example.bishe.dto.UserProfileVO;
import com.example.bishe.dto.UserWarningVO;
import com.example.bishe.dto.ViolationHandleRequest;
import com.example.bishe.entity.ChatRecord;
import com.example.bishe.entity.ChatViolation;
import com.example.bishe.entity.GroupChat;
import com.example.bishe.entity.SpecialWord;
import com.example.bishe.entity.Student;
import com.example.bishe.entity.Teacher;
import com.example.bishe.entity.UserWarning;
import com.example.bishe.mapper.ChatRecordMapper;
import com.example.bishe.mapper.ChatViolationMapper;
import com.example.bishe.mapper.GroupChatMapper;
import com.example.bishe.mapper.SpecialWordMapper;
import com.example.bishe.mapper.StudentMapper;
import com.example.bishe.mapper.TeacherMapper;
import com.example.bishe.mapper.UserWarningMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 特殊词监视、警告弹窗、师生账号软封禁。
 */
@Service
public class ModerationService {

    public static final int VIOLATION_PENDING = 0;
    public static final int VIOLATION_IGNORED = 1;
    public static final int VIOLATION_WARNED = 2;
    public static final int VIOLATION_BANNED = 3;

    /** 系统通知发送人ID */
    public static final long SYSTEM_SENDER_ID = 0L;
    /** 系统警告消息类型 */
    public static final int MSG_SYSTEM_WARN = 2;

    public static final String DEFAULT_WARN_CONTENT =
            "文明用语：请注意网络言行，使用礼貌、友善的表达。"
                    + "辱骂、人身攻击、歧视性言论等不当内容一经查实，平台将予以警告或封禁处理。"
                    + "请共同维护清朗校园交流环境。";

    private final SpecialWordMapper specialWordMapper;
    private final ChatViolationMapper chatViolationMapper;
    private final UserWarningMapper userWarningMapper;
    private final ChatRecordMapper chatRecordMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final GroupChatMapper groupChatMapper;
    private final SocialUserService socialUserService;
    private final ChatPushService chatPushService;
    private final AdminOperationService operations;

    public ModerationService(SpecialWordMapper specialWordMapper,
                             ChatViolationMapper chatViolationMapper,
                             UserWarningMapper userWarningMapper,
                             ChatRecordMapper chatRecordMapper,
                             StudentMapper studentMapper,
                             TeacherMapper teacherMapper,
                             GroupChatMapper groupChatMapper,
                             SocialUserService socialUserService,
                             ChatPushService chatPushService,AdminOperationService operations) {
        this.specialWordMapper = specialWordMapper;
        this.chatViolationMapper = chatViolationMapper;
        this.userWarningMapper = userWarningMapper;
        this.chatRecordMapper = chatRecordMapper;
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.groupChatMapper = groupChatMapper;
        this.socialUserService = socialUserService;
        this.chatPushService = chatPushService;
        this.operations=operations;
    }

    // ---------- 特殊词 CRUD ----------

    public List<SpecialWord> listWords() {
        return specialWordMapper.findAll();
    }

    @Transactional
    public SpecialWord addWord(SpecialWordRequest request, String adminId) {
        if (request == null || !StringUtils.hasText(request.getWord())) {
            throw new BusinessException("特殊词不能为空");
        }
        String word = request.getWord().trim();
        if(word.length()>100)throw new BusinessException("特殊词最多100字");
        if (specialWordMapper.findByWord(word) != null) {
            throw new BusinessException("该特殊词已存在");
        }
        SpecialWord entity = new SpecialWord();
        entity.setWord(word);
        entity.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        entity.setCreatedBy(adminId);
        specialWordMapper.insert(entity);
        operations.record("新增共用屏蔽词","WORD",entity.getWordId(),"",null,"",word);
        return specialWordMapper.findById(entity.getWordId());
    }

    @Transactional
    public void setWordEnabled(Long wordId, int enabled) {
        if (specialWordMapper.findById(wordId) == null) {
            throw new BusinessException("特殊词不存在");
        }
        specialWordMapper.updateEnabled(wordId, enabled == 1 ? 1 : 0);
        operations.record(enabled==1?"启用共用屏蔽词":"停用共用屏蔽词","WORD",wordId,"",null,"",specialWordMapper.findById(wordId).getWord());
    }

    @Transactional
    public void deleteWord(Long wordId) {
        if (specialWordMapper.findById(wordId) == null) {
            throw new BusinessException("特殊词不存在");
        }
        String word=specialWordMapper.findById(wordId).getWord();
        specialWordMapper.deleteById(wordId);
        operations.record("删除共用屏蔽词","WORD",wordId,"",null,"",word);
    }

    /**
     * 发送消息后扫描特殊词并生成待处理命中记录(不拦截发送)。
     */
    @Transactional
    public void scanAndRecord(ChatRecord message) {
        if (message == null || message.getMessageId() == null) {
            return;
        }
        if (message.getMessageType() != null && message.getMessageType() != 0) {
            return;
        }
        String content = message.getContent();
        if (!StringUtils.hasText(content)) {
            return;
        }
        List<SpecialWord> words = specialWordMapper.findEnabled();
        if (words == null || words.isEmpty()) {
            return;
        }
        for (SpecialWord word : words) {
            if (word.getWord() == null || word.getWord().isEmpty()) {
                continue;
            }
            if (content.contains(word.getWord())) {
                ChatViolation violation = new ChatViolation();
                violation.setMessageId(message.getMessageId());
                violation.setWordId(word.getWordId());
                violation.setMatchedWord(word.getWord());
                violation.setSenderId(message.getSenderId());
                violation.setReceiverId(message.getReceiverId() == null ? 0L : message.getReceiverId());
                violation.setIsPrivate(message.getIsPrivate() == null ? 0 : message.getIsPrivate());
                violation.setGroupId(message.getGroupId() == null ? 0L : message.getGroupId());
                violation.setContentSnapshot(content);
                violation.setStatus(VIOLATION_PENDING);
                chatViolationMapper.insert(violation);
            }
        }
    }

    public Map<String, Object> listViolations(Integer status, int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 20;
        }
        int offset = (page - 1) * size;
        List<ChatViolation> rows = chatViolationMapper.findPage(status, offset, size);
        List<ChatViolationVO> list = new ArrayList<>();
        for (ChatViolation row : rows) {
            list.add(toViolationVO(row));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", chatViolationMapper.countPage(status));
        result.put("pendingCount", chatViolationMapper.countPending());
        result.put("page", page);
        result.put("size", size);
        result.put("list", list);
        return result;
    }

    @Transactional
    public void handleViolation(ViolationHandleRequest request, String adminId) {
        if (request == null || request.getViolationId() == null) {
            throw new BusinessException("违规记录ID不能为空");
        }
        if (!StringUtils.hasText(request.getAction())) {
            throw new BusinessException("处理动作不能为空");
        }
        ChatViolation violation = chatViolationMapper.findById(request.getViolationId());
        if (violation == null) {
            throw new BusinessException("违规记录不存在");
        }
        if (violation.getStatus() != null && violation.getStatus() != VIOLATION_PENDING) {
            throw new BusinessException("该记录已处理");
        }
        String action = request.getAction().trim().toUpperCase();
        switch (action) {
            case "IGNORE" -> {
                chatViolationMapper.updateHandle(violation.getViolationId(), VIOLATION_IGNORED,
                        adminId, request.getNote());
                operations.record("忽略聊天特殊词命中","CHAT",violation.getMessageId(),Long.toString(violation.getSenderId()),null,"",request.getNote());
            }
            case "WARN" -> {
                String content = StringUtils.hasText(request.getWarnContent())
                        ? request.getWarnContent().trim()
                        : DEFAULT_WARN_CONTENT;
                issueWarning(violation.getSenderId(), content,
                        "特殊词命中: " + violation.getMatchedWord(),
                        violation.getMessageId(), violation.getViolationId(), adminId);
                chatViolationMapper.updateHandle(violation.getViolationId(), VIOLATION_WARNED,
                        adminId, request.getNote());
            }
            case "BAN" -> {
                String reason = StringUtils.hasText(request.getBanReason())
                        ? request.getBanReason().trim()
                        : "聊天特殊词违规: " + violation.getMatchedWord();
                banUser(violation.getSenderId(), reason, adminId);
                chatViolationMapper.updateHandle(violation.getViolationId(), VIOLATION_BANNED,
                        adminId, request.getNote());
            }
            default -> throw new BusinessException("不支持的处理动作, 请使用 IGNORE/WARN/BAN");
        }
    }

    // ---------- 警告 ----------

    @Transactional
    public UserWarning issueWarning(Long userId, String content, String reason,
                                    Long relatedMessageId, Long relatedViolationId, String adminId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        assertUserExists(userId);
        String warnContent = StringUtils.hasText(content) ? content.trim() : DEFAULT_WARN_CONTENT;

        ChatRecord sysMsg = new ChatRecord();
        sysMsg.setSenderId(SYSTEM_SENDER_ID);
        sysMsg.setReceiverId(userId);
        sysMsg.setIsPrivate(1);
        sysMsg.setGroupId(0L);
        sysMsg.setContent(warnContent);
        sysMsg.setMessageType(MSG_SYSTEM_WARN);
        sysMsg.setReferenceId(0L);
        sysMsg.setIsDeleted(0);
        chatRecordMapper.insert(sysMsg);

        UserWarning warning = new UserWarning();
        warning.setUserId(userId);
        warning.setContent(warnContent);
        warning.setReason(reason);
        warning.setRelatedMessageId(relatedMessageId);
        warning.setRelatedViolationId(relatedViolationId);
        warning.setCreatedBy(adminId);
        warning.setIsRead(0);
        warning.setIsDismissed(0);
        warning.setChatMessageId(sysMsg.getMessageId());
        userWarningMapper.insert(warning);
        operations.record("警告用户","USER",userId,Long.toString(userId),null,"",reason+"；"+warnContent);

        try {
            chatPushService.pushNewMessage(toSystemMessageVO(sysMsg));
            chatPushService.pushUserWarning(userId, toWarningVOList(List.of(warning)).get(0));
        } catch (Exception ignored) {
            // 推送失败不影响落库
        }
        return warning;
    }

    public List<UserWarningVO> listUnreadWarnings(Long userId) {
        List<UserWarning> rows = userWarningMapper.findUnreadByUser(userId);
        return toWarningVOList(rows);
    }

    public List<UserWarningVO> listVisibleWarnings(Long userId) {
        return toWarningVOList(userWarningMapper.findVisibleByUser(userId));
    }

    @Transactional
    public void markWarningRead(Long userId, Long warningId) {
        if (warningId == null) {
            userWarningMapper.markAllRead(userId);
            return;
        }
        UserWarning warning = userWarningMapper.findById(warningId);
        if (warning == null || !userId.equals(warning.getUserId())) {
            throw new BusinessException("警告记录不存在");
        }
        userWarningMapper.markRead(warningId, userId);
    }

    @Transactional
    public void dismissWarning(Long userId, Long warningId) {
        UserWarning warning = userWarningMapper.findById(warningId);
        if (warning == null || !userId.equals(warning.getUserId())) {
            throw new BusinessException("警告记录不存在");
        }
        userWarningMapper.dismiss(warningId, userId);
        if (warning.getChatMessageId() != null) {
            chatRecordMapper.softDelete(warning.getChatMessageId());
        }
    }

    // ---------- 封禁 / 解禁 / 软删 ----------

    @Transactional
    public void banAccount(AccountBanRequest request, String adminId) {
        if (request == null || request.getAccountId() == null) {
            throw new BusinessException("账号ID不能为空");
        }
        String reason = StringUtils.hasText(request.getBanReason()) ? request.getBanReason().trim() : "违规封禁";
        String type = request.getAccountType() == null ? "" : request.getAccountType().trim().toLowerCase();
        if ("student".equals(type) || String.valueOf(request.getAccountId()).startsWith("1")) {
            Student student = studentMapper.findById(request.getAccountId());
            if (student == null) {
                throw new BusinessException("学生不存在");
            }
            studentMapper.ban(request.getAccountId(), reason, adminId);
            operations.record("封禁账号","USER",request.getAccountId(),"STUDENT:"+request.getAccountId(),null,"",reason);
            notifyForceLogout(request.getAccountId(), reason);
            return;
        }
        if ("teacher".equals(type) || String.valueOf(request.getAccountId()).startsWith("2")) {
            Teacher teacher = teacherMapper.findById(request.getAccountId());
            if (teacher == null) {
                throw new BusinessException("教师不存在");
            }
            teacherMapper.ban(request.getAccountId(), reason, adminId);
            operations.record("封禁账号","USER",request.getAccountId(),"TEACHER:"+request.getAccountId(),null,"",reason);
            notifyForceLogout(request.getAccountId(), reason);
            return;
        }
        banUser(request.getAccountId(), reason, adminId);
    }

    @Transactional
    public void unbanAccount(String accountType, Long accountId) {
        if (accountId == null) {
            throw new BusinessException("账号ID不能为空");
        }
        String type = accountType == null ? "" : accountType.trim().toLowerCase();
        if ("student".equals(type) || String.valueOf(accountId).startsWith("1")) {
            if (studentMapper.findById(accountId) == null) {
                throw new BusinessException("学生不存在");
            }
            studentMapper.unban(accountId);
            operations.record("解封账号","USER",accountId,"STUDENT:"+accountId,null,"","");
            return;
        }
        if ("teacher".equals(type) || String.valueOf(accountId).startsWith("2")) {
            if (teacherMapper.findById(accountId) == null) {
                throw new BusinessException("教师不存在");
            }
            teacherMapper.unban(accountId);
            operations.record("解封账号","USER",accountId,"TEACHER:"+accountId,null,"","");
            return;
        }
        throw new BusinessException("无法识别账号类型");
    }

    @Transactional
    public void softDeleteAccount(String accountType, Long accountId, String reason, String adminId) {
        if (accountId == null) {
            throw new BusinessException("账号ID不能为空");
        }
        String banReason = StringUtils.hasText(reason) ? reason.trim() : "管理员软删除";
        String type = accountType == null ? "" : accountType.trim().toLowerCase();
        if ("student".equals(type) || String.valueOf(accountId).startsWith("1")) {
            if (studentMapper.findById(accountId) == null) {
                throw new BusinessException("学生不存在");
            }
            studentMapper.softDelete(accountId, banReason, adminId);
            operations.record("软删除账号","USER",accountId,"STUDENT:"+accountId,null,"",banReason);
            notifyForceLogout(accountId, banReason);
            return;
        }
        if ("teacher".equals(type) || String.valueOf(accountId).startsWith("2")) {
            if (teacherMapper.findById(accountId) == null) {
                throw new BusinessException("教师不存在");
            }
            teacherMapper.softDelete(accountId, banReason, adminId);
            operations.record("软删除账号","USER",accountId,"TEACHER:"+accountId,null,"",banReason);
            notifyForceLogout(accountId, banReason);
            return;
        }
        throw new BusinessException("无法识别账号类型");
    }

    public void assertAccountActive(Long userId) {
        if (userId == null) {
            return;
        }
        Student student = studentMapper.findById(userId);
        if (student != null) {
            assertStudentActive(student);
            return;
        }
        Teacher teacher = teacherMapper.findById(userId);
        if (teacher != null) {
            assertTeacherActive(teacher);
        }
    }

    public void assertStudentActive(Student student) {
        if (student == null) {
            throw new BusinessException("账号不存在");
        }
        if (student.getIsDeleted() != null && student.getIsDeleted() == 1) {
            throw new BusinessException("账号已注销");
        }
        if (student.getIsBanned() != null && student.getIsBanned() == 1) {
            throwBanned(student.getBanReason());
        }
    }

    public void assertTeacherActive(Teacher teacher) {
        if (teacher == null) {
            throw new BusinessException("账号不存在");
        }
        if (teacher.getIsDeleted() != null && teacher.getIsDeleted() == 1) {
            throw new BusinessException("账号已注销");
        }
        if (teacher.getIsBanned() != null && teacher.getIsBanned() == 1) {
            throwBanned(teacher.getBanReason());
        }
    }

    public void throwBanned(String banReason) {
        throw new BusinessException(403, "该账号已经被永久封禁", BanInfo.of(banReason));
    }

    private void banUser(Long userId, String reason, String adminId) {
        Student student = studentMapper.findById(userId);
        if (student != null) {
            studentMapper.ban(userId, reason, adminId);
            operations.record("封禁账号","USER",userId,"STUDENT:"+userId,null,"",reason);
            notifyForceLogout(userId, reason);
            return;
        }
        Teacher teacher = teacherMapper.findById(userId);
        if (teacher != null) {
            teacherMapper.ban(userId, reason, adminId);
            operations.record("封禁账号","USER",userId,"TEACHER:"+userId,null,"",reason);
            notifyForceLogout(userId, reason);
            return;
        }
        throw new BusinessException("用户不存在, 无法封禁");
    }

    private void notifyForceLogout(Long userId, String reason) {
        try {
            chatPushService.pushForceLogout(userId, BanInfo.of(reason));
        } catch (Exception ignored) {
            // 推送失败不影响封禁落库
        }
    }

    private void assertUserExists(Long userId) {
        if (studentMapper.findById(userId) == null && teacherMapper.findById(userId) == null) {
            throw new BusinessException("用户不存在");
        }
    }

    private ChatViolationVO toViolationVO(ChatViolation row) {
        ChatViolationVO vo = new ChatViolationVO();
        vo.setViolationId(row.getViolationId());
        vo.setMessageId(row.getMessageId());
        vo.setWordId(row.getWordId());
        vo.setMatchedWord(row.getMatchedWord());
        vo.setSenderId(row.getSenderId());
        vo.setReceiverId(row.getReceiverId());
        vo.setIsPrivate(row.getIsPrivate());
        vo.setGroupId(row.getGroupId());
        vo.setContentSnapshot(row.getContentSnapshot());
        vo.setHitTime(row.getHitTime());
        vo.setStatus(row.getStatus());
        vo.setHandledBy(row.getHandledBy());
        vo.setHandledAt(row.getHandledAt());
        vo.setHandleNote(row.getHandleNote());

        UserProfileVO sender = socialUserService.findProfile(row.getSenderId());
        if (sender != null) {
            vo.setSenderName(sender.getDisplayName());
        }
        if (row.getIsPrivate() != null && row.getIsPrivate() == 1
                && row.getReceiverId() != null && row.getReceiverId() > 0) {
            UserProfileVO receiver = socialUserService.findProfile(row.getReceiverId());
            if (receiver != null) {
                vo.setReceiverName(receiver.getDisplayName());
            }
        }
        if (row.getIsPrivate() != null && row.getIsPrivate() == 0
                && row.getGroupId() != null && row.getGroupId() > 0) {
            GroupChat group = groupChatMapper.findById(row.getGroupId());
            if (group != null) {
                vo.setGroupName(group.getGroupName());
            }
        }
        return vo;
    }

    private List<UserWarningVO> toWarningVOList(List<UserWarning> rows) {
        List<UserWarningVO> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (UserWarning row : rows) {
            UserWarningVO vo = new UserWarningVO();
            vo.setWarningId(row.getWarningId());
            vo.setUserId(row.getUserId());
            vo.setContent(row.getContent());
            vo.setReason(row.getReason());
            vo.setRelatedMessageId(row.getRelatedMessageId());
            vo.setRelatedViolationId(row.getRelatedViolationId());
            vo.setCreatedBy(row.getCreatedBy());
            vo.setCreateTime(row.getCreateTime());
            vo.setIsRead(row.getIsRead());
            vo.setIsDismissed(row.getIsDismissed());
            vo.setChatMessageId(row.getChatMessageId());
            list.add(vo);
        }
        return list;
    }

    private com.example.bishe.dto.ChatMessageVO toSystemMessageVO(ChatRecord record) {
        com.example.bishe.dto.ChatMessageVO vo = new com.example.bishe.dto.ChatMessageVO();
        vo.setMessageId(record.getMessageId());
        vo.setSendTime(record.getSendTime());
        vo.setSenderId(record.getSenderId());
        vo.setReceiverId(record.getReceiverId());
        vo.setIsPrivate(1);
        vo.setGroupId(0L);
        vo.setContent(record.getContent());
        vo.setMessageType(MSG_SYSTEM_WARN);
        vo.setReferenceId(0L);
        vo.setIsDeleted(0);
        return vo;
    }
}

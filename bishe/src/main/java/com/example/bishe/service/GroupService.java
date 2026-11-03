package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.dto.GroupAppointAdminRequest;
import com.example.bishe.dto.GroupCreateRequest;
import com.example.bishe.dto.GroupDetailVO;
import com.example.bishe.dto.GroupMemberVO;
import com.example.bishe.dto.GroupUpdateRequest;
import com.example.bishe.entity.GroupAdmin;
import com.example.bishe.entity.GroupChat;
import com.example.bishe.entity.GroupUser;
import com.example.bishe.mapper.GroupAdminMapper;
import com.example.bishe.mapper.GroupChatMapper;
import com.example.bishe.mapper.GroupUserMapper;
import com.example.bishe.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 群聊系统: 创建/搜索/加入/退出/解散/任命管理员/改资料。
 * 群主(adminLevel=1)退出即解散; 群主不可普通退群。
 */
@Service
public class GroupService {

    public static final int ADMIN_OWNER = 1;
    public static final int ADMIN_NORMAL = 2;

    private final GroupChatMapper groupChatMapper;
    private final GroupUserMapper groupUserMapper;
    private final GroupAdminMapper groupAdminMapper;
    private final SocialUserService socialUserService;
    private final AdminOperationService operations;

    public GroupService(GroupChatMapper groupChatMapper,
                        GroupUserMapper groupUserMapper,
                        GroupAdminMapper groupAdminMapper,
                        SocialUserService socialUserService,AdminOperationService operations) {
        this.groupChatMapper = groupChatMapper;
        this.groupUserMapper = groupUserMapper;
        this.groupAdminMapper = groupAdminMapper;
        this.socialUserService = socialUserService;
        this.operations=operations;
    }

    @Transactional
    public GroupChat createGroup(Long creatorId, GroupCreateRequest request) {
        if (request.getGroupName() == null || request.getGroupName().trim().isEmpty()) {
            throw new BusinessException("群名不能为空");
        }
        Long groupId = generateUniqueGroupId();
        GroupChat group = new GroupChat();
        group.setGroupId(groupId);
        group.setTotalPeople(1);
        group.setGroupName(request.getGroupName().trim());
        group.setGroupAvatar(request.getGroupAvatar());
        group.setGroupDescription(request.getGroupDescription());
        group.setIsDissolved(0);
        group.setIsDeleted(0);
        group.setIsBanned(0);
        groupChatMapper.insert(group);

        GroupUser member = new GroupUser();
        member.setGroupId(groupId);
        member.setUserId(creatorId);
        groupUserMapper.insert(member);

        GroupAdmin owner = new GroupAdmin();
        owner.setGroupId(groupId);
        owner.setAdminId(creatorId);
        owner.setAdminLevel(ADMIN_OWNER);
        groupAdminMapper.insert(owner);

        return groupChatMapper.findById(groupId);
    }

    public List<GroupChat> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new BusinessException("搜索关键词不能为空");
        }
        return groupChatMapper.search(keyword.trim());
    }

    public GroupDetailVO getDetail(Long userId, Long groupId) {
        GroupChat group = requireActiveGroup(groupId);
        requireMember(groupId, userId);
        return toDetailVO(group, userId);
    }

    public List<GroupDetailVO> myGroups(Long userId) {
        List<GroupUser> memberships = groupUserMapper.findActiveByUserId(userId);
        List<GroupDetailVO> result = new ArrayList<>();
        for (GroupUser membership : memberships) {
            GroupChat group = groupChatMapper.findById(membership.getGroupId());
            if (group != null && isGroupUsable(group)) {
                result.add(toDetailVO(group, userId));
            }
        }
        return result;
    }

    /**
     * 系统管理员软删除群聊(非解散; 历史消息保留)。
     */
    @Transactional
    public void softDeleteByAdmin(Long groupId) {
        GroupChat group = requireGroupExists(groupId);
        if (group.getIsDeleted() != null && group.getIsDeleted() == 1) {
            throw new BusinessException("该群聊已被软删除");
        }
        groupChatMapper.softDelete(groupId);
        operations.record("软删除群聊","GROUP",groupId,"",null,"",group.getGroupName());
    }

    /**
     * 系统管理员封禁群聊(可附理由; 非解散)。
     */
    @Transactional
    public void banByAdmin(Long groupId, String banReason, String adminId) {
        GroupChat group = requireGroupExists(groupId);
        if (group.getIsBanned() != null && group.getIsBanned() == 1) {
            throw new BusinessException("该群聊已被封禁");
        }
        if (banReason == null || banReason.trim().isEmpty()) {
            throw new BusinessException("封禁理由不能为空");
        }
        groupChatMapper.ban(groupId, banReason.trim(), adminId, java.time.LocalDateTime.now());
        operations.record("封禁群聊","GROUP",groupId,"",null,"",group.getGroupName()+"；"+banReason);
    }

    /**
     * 系统管理员解除封禁。
     */
    @Transactional
    public void unbanByAdmin(Long groupId) {
        GroupChat group = requireGroupExists(groupId);
        if (group.getIsBanned() == null || group.getIsBanned() == 0) {
            throw new BusinessException("该群聊未被封禁");
        }
        groupChatMapper.unban(groupId);
        operations.record("解封群聊","GROUP",groupId,"",null,"",group.getGroupName());
    }

    /**
     * 管理员分页查看全部群聊(含解散/软删/封禁)。
     */
    public java.util.Map<String, Object> listAllForAdmin(int page, int size) {
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;
        int offset = (page - 1) * size;
        int total = groupChatMapper.countAll();
        List<GroupChat> groups = groupChatMapper.findAllForAdmin(offset, size);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("records", groups);
        return result;
    }

    @Transactional
    public void join(Long userId, Long groupId) {
        GroupChat group = requireActiveGroup(groupId);
        if (groupUserMapper.find(groupId, userId) != null) {
            throw new BusinessException("你已在该群聊中");
        }
        GroupUser member = new GroupUser();
        member.setGroupId(groupId);
        member.setUserId(userId);
        groupUserMapper.insert(member);
        int count = groupUserMapper.countByGroupId(groupId);
        groupChatMapper.updateTotalPeople(groupId, count);
    }

    /**
     * 退出群聊。群主不可退出, 需走解散。
     */
    @Transactional
    public void leave(Long userId, Long groupId) {
        requireActiveGroup(groupId);
        requireMember(groupId, userId);
        GroupAdmin admin = groupAdminMapper.find(groupId, userId);
        if (admin != null && ADMIN_OWNER == admin.getAdminLevel()) {
            throw new BusinessException("群主无法退出群聊, 请使用解散群聊");
        }
        if (admin != null) {
            groupAdminMapper.delete(groupId, userId);
        }
        groupUserMapper.delete(groupId, userId);
        int count = groupUserMapper.countByGroupId(groupId);
        groupChatMapper.updateTotalPeople(groupId, count);
    }

    /**
     * 解散群聊(仅群主)。历史消息保留, 群标记为已解散。
     */
    @Transactional
    public void dissolve(Long userId, Long groupId) {
        requireActiveGroup(groupId);
        requireOwner(groupId, userId);
        groupChatMapper.dissolve(groupId);
        // 保留成员与管理员记录便于审计; 业务侧以 is_dissolved 拦截后续操作
    }

    @Transactional
    public void updateInfo(Long userId, GroupUpdateRequest request) {
        if (request.getGroupId() == null) {
            throw new BusinessException("群号不能为空");
        }
        GroupChat group = requireActiveGroup(request.getGroupId());
        requireAdmin(request.getGroupId(), userId);
        if (request.getGroupName() != null && !request.getGroupName().trim().isEmpty()) {
            group.setGroupName(request.getGroupName().trim());
        }
        if (request.getGroupAvatar() != null) {
            group.setGroupAvatar(request.getGroupAvatar());
        }
        if (request.getGroupDescription() != null) {
            group.setGroupDescription(request.getGroupDescription());
        }
        groupChatMapper.updateInfo(group);
    }

    /** 群主任命普通管理员。 */
    @Transactional
    public void appointAdmin(Long userId, GroupAppointAdminRequest request) {
        if (request.getGroupId() == null || request.getTargetUserId() == null) {
            throw new BusinessException("群号与目标用户不能为空");
        }
        requireActiveGroup(request.getGroupId());
        requireOwner(request.getGroupId(), userId);
        requireMember(request.getGroupId(), request.getTargetUserId());
        if (userId.equals(request.getTargetUserId())) {
            throw new BusinessException("不能任命自己");
        }
        GroupAdmin existing = groupAdminMapper.find(request.getGroupId(), request.getTargetUserId());
        if (existing != null) {
            if (ADMIN_OWNER == existing.getAdminLevel()) {
                throw new BusinessException("不能改变群主身份");
            }
            groupAdminMapper.updateLevel(request.getGroupId(), request.getTargetUserId(), ADMIN_NORMAL);
            return;
        }
        GroupAdmin admin = new GroupAdmin();
        admin.setGroupId(request.getGroupId());
        admin.setAdminId(request.getTargetUserId());
        admin.setAdminLevel(ADMIN_NORMAL);
        groupAdminMapper.insert(admin);
    }

    /** 群主撤销普通管理员。 */
    @Transactional
    public void removeAdmin(Long userId, GroupAppointAdminRequest request) {
        if (request.getGroupId() == null || request.getTargetUserId() == null) {
            throw new BusinessException("群号与目标用户不能为空");
        }
        requireActiveGroup(request.getGroupId());
        requireOwner(request.getGroupId(), userId);
        GroupAdmin target = groupAdminMapper.find(request.getGroupId(), request.getTargetUserId());
        if (target == null) {
            throw new BusinessException("对方不是管理员");
        }
        if (ADMIN_OWNER == target.getAdminLevel()) {
            throw new BusinessException("不能撤销群主");
        }
        groupAdminMapper.delete(request.getGroupId(), request.getTargetUserId());
    }

    /**
     * 移出群成员。群主可移出普通管理员和成员；普通管理员仅可移出普通成员。
     */
    @Transactional
    public void removeMember(Long userId, GroupAppointAdminRequest request) {
        if (request.getGroupId() == null || request.getTargetUserId() == null) {
            throw new BusinessException("群号与目标用户不能为空");
        }
        Long groupId = request.getGroupId();
        Long targetUserId = request.getTargetUserId();
        requireActiveGroup(groupId);
        requireMember(groupId, userId);
        GroupAdmin operator = groupAdminMapper.find(groupId, userId);
        if (operator == null) {
            throw new BusinessException(403, "需要群管理员权限");
        }
        requireMember(groupId, targetUserId);
        if (userId.equals(targetUserId)) {
            throw new BusinessException("不能将自己移出群聊，请使用退出群聊");
        }

        GroupAdmin target = groupAdminMapper.find(groupId, targetUserId);
        if (target != null && ADMIN_OWNER == target.getAdminLevel()) {
            throw new BusinessException(403, "不能移出群主");
        }
        if (ADMIN_NORMAL == operator.getAdminLevel() && target != null) {
            throw new BusinessException(403, "普通管理员不能移出其他管理员");
        }
        if (target != null) {
            groupAdminMapper.delete(groupId, targetUserId);
        }
        groupUserMapper.delete(groupId, targetUserId);
        groupChatMapper.updateTotalPeople(groupId, groupUserMapper.countByGroupId(groupId));
    }

    public List<GroupMemberVO> listMembers(Long userId, Long groupId) {
        requireActiveGroup(groupId);
        requireMember(groupId, userId);
        List<GroupUser> members = groupUserMapper.findByGroupId(groupId);
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> memberIds = members.stream()
                .map(GroupUser::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, com.example.bishe.dto.UserProfileVO> profileMap = socialUserService.findProfiles(memberIds);
        Map<Long, Integer> adminLevelMap = new HashMap<>();
        for (GroupAdmin admin : groupAdminMapper.findByGroupId(groupId)) {
            adminLevelMap.put(admin.getAdminId(), admin.getAdminLevel());
        }
        List<GroupMemberVO> result = new ArrayList<>();
        for (GroupUser member : members) {
            GroupMemberVO vo = new GroupMemberVO();
            vo.setUserId(member.getUserId());
            vo.setProfile(profileMap.get(member.getUserId()));
            vo.setAdminLevel(adminLevelMap.get(member.getUserId()));
            result.add(vo);
        }
        return result;
    }

    // ---------- 校验工具(供 ChatService 复用) ----------

    public GroupChat requireActiveGroup(Long groupId) {
        GroupChat group = requireGroupExists(groupId);
        if (group.getIsDissolved() != null && group.getIsDissolved() == 1) {
            throw new BusinessException("该群聊已解散");
        }
        if (group.getIsDeleted() != null && group.getIsDeleted() == 1) {
            throw new BusinessException("该群聊已被删除");
        }
        if (group.getIsBanned() != null && group.getIsBanned() == 1) {
            String reason = group.getBanReason();
            if (reason == null || reason.isEmpty()) {
                throw new BusinessException("该群聊已被封禁");
            }
            throw new BusinessException("该群聊已被封禁: " + reason);
        }
        return group;
    }

    public GroupChat requireGroupExists(Long groupId) {
        if (groupId == null) {
            throw new BusinessException("群号不能为空");
        }
        GroupChat group = groupChatMapper.findById(groupId);
        if (group == null) {
            throw new BusinessException("群聊不存在");
        }
        return group;
    }

    private boolean isGroupUsable(GroupChat group) {
        if (group.getIsDissolved() != null && group.getIsDissolved() == 1) {
            return false;
        }
        if (group.getIsDeleted() != null && group.getIsDeleted() == 1) {
            return false;
        }
        if (group.getIsBanned() != null && group.getIsBanned() == 1) {
            return false;
        }
        return true;
    }

    public void requireMember(Long groupId, Long userId) {
        if (groupUserMapper.find(groupId, userId) == null) {
            throw new BusinessException("你不在该群聊中");
        }
    }

    public void requireAdmin(Long groupId, Long userId) {
        GroupAdmin admin = groupAdminMapper.find(groupId, userId);
        if (admin == null) {
            throw new BusinessException("需要群管理员权限");
        }
    }

    public void requireOwner(Long groupId, Long userId) {
        GroupAdmin admin = groupAdminMapper.find(groupId, userId);
        if (admin == null || ADMIN_OWNER != admin.getAdminLevel()) {
            throw new BusinessException("需要群主权限");
        }
    }

    public Integer getAdminLevel(Long groupId, Long userId) {
        GroupAdmin admin = groupAdminMapper.find(groupId, userId);
        return admin == null ? null : admin.getAdminLevel();
    }

    private GroupDetailVO toDetailVO(GroupChat group, Long userId) {
        GroupDetailVO vo = new GroupDetailVO();
        vo.setGroupId(group.getGroupId());
        vo.setGroupName(group.getGroupName());
        vo.setGroupAvatar(group.getGroupAvatar());
        vo.setGroupDescription(group.getGroupDescription());
        vo.setTotalPeople(group.getTotalPeople());
        vo.setIsDissolved(group.getIsDissolved());
        vo.setIsDeleted(group.getIsDeleted());
        vo.setIsBanned(group.getIsBanned());
        vo.setBanReason(group.getBanReason());
        vo.setBannedBy(group.getBannedBy());
        vo.setBannedAt(group.getBannedAt());
        vo.setMyAdminLevel(getAdminLevel(group.getGroupId(), userId));
        return vo;
    }

    private Long generateUniqueGroupId() {
        for (int i = 0; i < 20; i++) {
            Long id = IdGenerator.generateGroupId();
            if (groupChatMapper.findById(id) == null) {
                return id;
            }
        }
        throw new BusinessException("群号生成失败, 请重试");
    }
}

package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.dto.FriendRequestVO;
import com.example.bishe.dto.UserProfileVO;
import com.example.bishe.entity.Friend;
import com.example.bishe.entity.FriendRequest;
import com.example.bishe.mapper.FriendMapper;
import com.example.bishe.mapper.FriendRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 好友系统: 申请/同意/拒绝/列表/删除。
 */
@Service
public class FriendService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_ACCEPTED = 1;
    public static final int STATUS_REJECTED = 2;

    private final FriendMapper friendMapper;
    private final FriendRequestMapper friendRequestMapper;
    private final SocialUserService socialUserService;

    public FriendService(FriendMapper friendMapper,
                         FriendRequestMapper friendRequestMapper,
                         SocialUserService socialUserService) {
        this.friendMapper = friendMapper;
        this.friendRequestMapper = friendRequestMapper;
        this.socialUserService = socialUserService;
    }

    /** 按用户ID搜索公开资料(用于加好友)。 */
    public UserProfileVO searchUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        return socialUserService.requireProfile(userId);
    }

    /** 按昵称/姓名模糊搜索用户(同名全部列出, 用于加好友)。 */
    public List<UserProfileVO> searchByNickname(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException("搜索关键词不能为空");
        }
        return socialUserService.searchByDisplayName(keyword.trim());
    }

    @Transactional
    public FriendRequest sendRequest(Long requesterId, Long receiverId) {
        if (receiverId == null) {
            throw new BusinessException("被申请人ID不能为空");
        }
        if (requesterId.equals(receiverId)) {
            throw new BusinessException("不能添加自己为好友");
        }
        socialUserService.requireProfile(receiverId);
        if (areFriends(requesterId, receiverId)) {
            throw new BusinessException("你们已经是好友");
        }
        if (friendRequestMapper.findPending(requesterId, receiverId) != null) {
            throw new BusinessException("已发送过好友申请, 请等待对方处理");
        }
        // 对方已向我发起待处理申请时提示去同意
        if (friendRequestMapper.findPending(receiverId, requesterId) != null) {
            throw new BusinessException("对方已向你发起好友申请, 请先处理");
        }

        FriendRequest request = new FriendRequest();
        request.setRequesterId(requesterId);
        request.setReceiverId(receiverId);
        request.setStatus(STATUS_PENDING);
        friendRequestMapper.insert(request);
        return friendRequestMapper.findById(request.getRequestId());
    }

    public List<FriendRequestVO> listReceivedPending(Long userId) {
        List<FriendRequest> list = friendRequestMapper.findPendingByReceiver(userId);
        return toRequestVOList(list);
    }

    public List<FriendRequestVO> listSent(Long userId) {
        return toRequestVOList(friendRequestMapper.findByRequester(userId));
    }

    @Transactional
    public void acceptRequest(Long userId, Long requestId) {
        FriendRequest request = requirePendingRequestForReceiver(userId, requestId);
        friendRequestMapper.updateStatus(requestId, STATUS_ACCEPTED);
        if (!areFriends(request.getRequesterId(), request.getReceiverId())) {
            Friend friend = normalizePair(request.getRequesterId(), request.getReceiverId());
            friendMapper.insert(friend);
        }
    }

    @Transactional
    public void rejectRequest(Long userId, Long requestId) {
        requirePendingRequestForReceiver(userId, requestId);
        friendRequestMapper.updateStatus(requestId, STATUS_REJECTED);
    }

    public List<UserProfileVO> listFriends(Long userId) {
        List<Friend> pairs = friendMapper.findByUserId(userId);
        if (pairs.isEmpty()) {
            return List.of();
        }
        List<Long> friendIds = pairs.stream()
                .map(pair -> pair.getFriendAId().equals(userId) ? pair.getFriendBId() : pair.getFriendAId())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserProfileVO> profileMap = socialUserService.findProfiles(friendIds);
        List<UserProfileVO> result = new ArrayList<>();
        for (Long friendId : friendIds) {
            UserProfileVO profile = profileMap.get(friendId);
            if (profile != null) {
                result.add(profile);
            }
        }
        return result;
    }

    @Transactional
    public void deleteFriend(Long userId, Long friendId) {
        if (friendId == null) {
            throw new BusinessException("好友ID不能为空");
        }
        if (!areFriends(userId, friendId)) {
            throw new BusinessException("对方不是你的好友");
        }
        Friend pair = normalizePair(userId, friendId);
        friendMapper.delete(pair.getFriendAId(), pair.getFriendBId());
    }

    public boolean areFriends(Long userA, Long userB) {
        Friend pair = normalizePair(userA, userB);
        return friendMapper.find(pair.getFriendAId(), pair.getFriendBId()) != null;
    }

    public void requireFriendship(Long userA, Long userB) {
        if (!areFriends(userA, userB)) {
            throw new BusinessException("对方不是你的好友");
        }
    }

    private FriendRequest requirePendingRequestForReceiver(Long userId, Long requestId) {
        if (requestId == null) {
            throw new BusinessException("申请ID不能为空");
        }
        FriendRequest request = friendRequestMapper.findById(requestId);
        if (request == null) {
            throw new BusinessException("好友申请不存在");
        }
        if (!userId.equals(request.getReceiverId())) {
            throw new BusinessException("无权处理该申请");
        }
        if (request.getStatus() == null || request.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该申请已处理");
        }
        return request;
    }

    private Friend normalizePair(Long a, Long b) {
        Friend friend = new Friend();
        if (a < b) {
            friend.setFriendAId(a);
            friend.setFriendBId(b);
        } else {
            friend.setFriendAId(b);
            friend.setFriendBId(a);
        }
        return friend;
    }

    private List<FriendRequestVO> toRequestVOList(List<FriendRequest> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = new ArrayList<>();
        for (FriendRequest request : list) {
            if (request.getRequesterId() != null) {
                userIds.add(request.getRequesterId());
            }
            if (request.getReceiverId() != null) {
                userIds.add(request.getReceiverId());
            }
        }
        Map<Long, UserProfileVO> profileMap = socialUserService.findProfiles(userIds);
        List<FriendRequestVO> result = new ArrayList<>();
        for (FriendRequest request : list) {
            FriendRequestVO vo = new FriendRequestVO();
            vo.setRequestId(request.getRequestId());
            vo.setRequesterId(request.getRequesterId());
            vo.setReceiverId(request.getReceiverId());
            vo.setStatus(request.getStatus());
            vo.setApplyTime(request.getApplyTime());
            vo.setRequester(profileMap.get(request.getRequesterId()));
            vo.setReceiver(profileMap.get(request.getReceiverId()));
            result.add(vo);
        }
        return result;
    }
}

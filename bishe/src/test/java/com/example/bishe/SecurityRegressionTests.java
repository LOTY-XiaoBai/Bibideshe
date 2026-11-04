package com.example.bishe;

import com.example.bishe.common.AuthUser;
import com.example.bishe.common.BusinessException;
import com.example.bishe.common.UserRole;
import com.example.bishe.dto.GroupAppointAdminRequest;
import com.example.bishe.entity.GroupAdmin;
import com.example.bishe.entity.GroupChat;
import com.example.bishe.entity.GroupUser;
import com.example.bishe.mapper.GroupAdminMapper;
import com.example.bishe.mapper.GroupChatMapper;
import com.example.bishe.mapper.GroupUserMapper;
import com.example.bishe.service.FileUploadService;
import com.example.bishe.service.GroupService;
import com.example.bishe.service.SocialUserService;
import com.example.bishe.util.TokenUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityRegressionTests {
    @Test
    void emptyReviewStatusMeansAllRecordsWhileOmittedStatusDefaultsToPending() throws Exception {
        var commerce = mock(com.example.bishe.service.MerchantCommerceService.class);
        var controller = new com.example.bishe.controller.MerchantController(
                mock(com.example.bishe.service.MerchantService.class), commerce,
                mock(com.example.bishe.service.MerchantLocationService.class));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/commerce/all").param("status", ""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        verify(commerce).list("all", true, "", 1, 12);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/commerce/all"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        verify(commerce).list("all", true, "PENDING", 1, 12);
    }

    @TempDir
    Path tempDir;

    @Test
    void tokenShouldCarrySessionIdAndRejectTampering() {
        TokenUtil.configure("test-token-secret-with-at-least-32-characters");
        String token = TokenUtil.createToken(new AuthUser(10001L, "10001", UserRole.STUDENT));

        AuthUser parsed = TokenUtil.parseToken(token);
        assertThat(parsed.getUserId()).isEqualTo(10001L);
        assertThat(parsed.getTokenId()).isNotBlank();
        assertThat(parsed.getExpireAt()).isGreaterThan(System.currentTimeMillis());

        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> TokenUtil.parseToken(tampered)).isInstanceOf(BusinessException.class);
    }

    @Test
    void renamedExecutableShouldNotPassImageUploadValidation() {
        FileUploadService service = new FileUploadService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "maxSizeMb", 10L);
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "MZ-not-an-image".getBytes());

        assertThatThrownBy(() -> service.uploadImage(disguised))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不匹配");
    }

    @Test
    void normalGroupAdminCanRemoveOrdinaryMember() {
        GroupChatMapper groupChatMapper = mock(GroupChatMapper.class);
        GroupUserMapper groupUserMapper = mock(GroupUserMapper.class);
        GroupAdminMapper groupAdminMapper = mock(GroupAdminMapper.class);
        GroupService service = new GroupService(groupChatMapper, groupUserMapper, groupAdminMapper,
                mock(SocialUserService.class),mock(com.example.bishe.service.AdminOperationService.class));
        long groupId = 30001L;
        long operatorId = 10001L;
        long targetId = 10002L;
        when(groupChatMapper.findById(groupId)).thenReturn(activeGroup(groupId));
        when(groupUserMapper.find(groupId, operatorId)).thenReturn(member(groupId, operatorId));
        when(groupUserMapper.find(groupId, targetId)).thenReturn(member(groupId, targetId));
        when(groupAdminMapper.find(groupId, operatorId)).thenReturn(admin(groupId, operatorId, GroupService.ADMIN_NORMAL));
        when(groupAdminMapper.find(groupId, targetId)).thenReturn(null);
        when(groupUserMapper.countByGroupId(groupId)).thenReturn(2);

        service.removeMember(operatorId, removeRequest(groupId, targetId));

        verify(groupUserMapper).delete(groupId, targetId);
        verify(groupChatMapper).updateTotalPeople(groupId, 2);
    }

    @Test
    void normalGroupAdminCannotRemoveAnotherAdmin() {
        GroupChatMapper groupChatMapper = mock(GroupChatMapper.class);
        GroupUserMapper groupUserMapper = mock(GroupUserMapper.class);
        GroupAdminMapper groupAdminMapper = mock(GroupAdminMapper.class);
        GroupService service = new GroupService(groupChatMapper, groupUserMapper, groupAdminMapper,
                mock(SocialUserService.class),mock(com.example.bishe.service.AdminOperationService.class));
        long groupId = 30001L;
        long operatorId = 10001L;
        long targetId = 10002L;
        when(groupChatMapper.findById(groupId)).thenReturn(activeGroup(groupId));
        when(groupUserMapper.find(groupId, operatorId)).thenReturn(member(groupId, operatorId));
        when(groupUserMapper.find(groupId, targetId)).thenReturn(member(groupId, targetId));
        when(groupAdminMapper.find(groupId, operatorId)).thenReturn(admin(groupId, operatorId, GroupService.ADMIN_NORMAL));
        when(groupAdminMapper.find(groupId, targetId)).thenReturn(admin(groupId, targetId, GroupService.ADMIN_NORMAL));

        assertThatThrownBy(() -> service.removeMember(operatorId, removeRequest(groupId, targetId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能移出其他管理员");
        verify(groupUserMapper, never()).delete(groupId, targetId);
    }

    private static GroupChat activeGroup(long groupId) {
        GroupChat group = new GroupChat();
        group.setGroupId(groupId);
        group.setIsDissolved(0);
        group.setIsDeleted(0);
        group.setIsBanned(0);
        return group;
    }

    private static GroupUser member(long groupId, long userId) {
        GroupUser member = new GroupUser();
        member.setGroupId(groupId);
        member.setUserId(userId);
        return member;
    }

    private static GroupAdmin admin(long groupId, long userId, int level) {
        GroupAdmin admin = new GroupAdmin();
        admin.setGroupId(groupId);
        admin.setAdminId(userId);
        admin.setAdminLevel(level);
        return admin;
    }

    private static GroupAppointAdminRequest removeRequest(long groupId, long targetId) {
        GroupAppointAdminRequest request = new GroupAppointAdminRequest();
        request.setGroupId(groupId);
        request.setTargetUserId(targetId);
        return request;
    }
}

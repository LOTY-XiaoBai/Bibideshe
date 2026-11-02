package com.example.bishe.mapper;

import com.example.bishe.entity.GroupChat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 群聊表数据访问接口 (表8 group_chat)。
 */
@Mapper
public interface GroupChatMapper {

    @Insert("INSERT INTO group_chat(group_id, total_people, group_name, group_avatar, group_description, " +
            "is_dissolved, is_deleted, is_banned) " +
            "VALUES(#{groupId}, #{totalPeople}, #{groupName}, #{groupAvatar}, #{groupDescription}, " +
            "#{isDissolved}, #{isDeleted}, #{isBanned})")
    int insert(GroupChat group);

    @Select("SELECT * FROM group_chat WHERE group_id = #{groupId}")
    GroupChat findById(Long groupId);

    @Select("SELECT * FROM group_chat WHERE is_dissolved = 0 AND is_deleted = 0 AND is_banned = 0 AND " +
            "(CAST(group_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%') OR group_name LIKE CONCAT('%', #{keyword}, '%'))")
    List<GroupChat> search(String keyword);

    @Select("SELECT * FROM group_chat ORDER BY group_id DESC LIMIT #{offset}, #{limit}")
    List<GroupChat> findAllForAdmin(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM group_chat")
    int countAll();

    @Update("UPDATE group_chat SET total_people = #{totalPeople} WHERE group_id = #{groupId}")
    int updateTotalPeople(@Param("groupId") Long groupId, @Param("totalPeople") Integer totalPeople);

    @Update("UPDATE group_chat SET group_name = #{groupName}, group_avatar = #{groupAvatar}, " +
            "group_description = #{groupDescription} WHERE group_id = #{groupId}")
    int updateInfo(GroupChat group);

    @Update("UPDATE group_chat SET is_dissolved = 1 WHERE group_id = #{groupId}")
    int dissolve(Long groupId);

    /** 系统管理员软删除群聊 */
    @Update("UPDATE group_chat SET is_deleted = 1 WHERE group_id = #{groupId}")
    int softDelete(Long groupId);

    /** 系统管理员封禁群聊 */
    @Update("UPDATE group_chat SET is_banned = 1, ban_reason = #{banReason}, banned_by = #{bannedBy}, " +
            "banned_at = #{bannedAt} WHERE group_id = #{groupId}")
    int ban(@Param("groupId") Long groupId,
            @Param("banReason") String banReason,
            @Param("bannedBy") String bannedBy,
            @Param("bannedAt") LocalDateTime bannedAt);

    /** 系统管理员解除封禁 */
    @Update("UPDATE group_chat SET is_banned = 0, ban_reason = NULL, banned_by = NULL, banned_at = NULL " +
            "WHERE group_id = #{groupId}")
    int unban(Long groupId);
}

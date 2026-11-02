package com.example.bishe.mapper;

import com.example.bishe.entity.UserWarning;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserWarningMapper {

    @Insert("INSERT INTO user_warning(user_id, content, reason, related_message_id, related_violation_id, " +
            "created_by, is_read, is_dismissed, chat_message_id) " +
            "VALUES(#{userId}, #{content}, #{reason}, #{relatedMessageId}, #{relatedViolationId}, " +
            "#{createdBy}, #{isRead}, #{isDismissed}, #{chatMessageId})")
    @Options(useGeneratedKeys = true, keyProperty = "warningId")
    int insert(UserWarning warning);

    @Select("SELECT * FROM user_warning WHERE warning_id = #{warningId}")
    UserWarning findById(Long warningId);

    @Select("SELECT * FROM user_warning WHERE user_id = #{userId} AND is_read = 0 " +
            "ORDER BY create_time ASC, warning_id ASC")
    List<UserWarning> findUnreadByUser(Long userId);

    @Select("SELECT * FROM user_warning WHERE user_id = #{userId} AND is_dismissed = 0 " +
            "ORDER BY create_time DESC, warning_id DESC")
    List<UserWarning> findVisibleByUser(Long userId);

    @Update("UPDATE user_warning SET is_read = 1 WHERE warning_id = #{warningId} AND user_id = #{userId}")
    int markRead(@Param("warningId") Long warningId, @Param("userId") Long userId);

    @Update("UPDATE user_warning SET is_read = 1 WHERE user_id = #{userId} AND is_read = 0")
    int markAllRead(Long userId);

    @Update("UPDATE user_warning SET is_dismissed = 1 WHERE warning_id = #{warningId} AND user_id = #{userId}")
    int dismiss(@Param("warningId") Long warningId, @Param("userId") Long userId);

    @Update("UPDATE user_warning SET chat_message_id = #{chatMessageId} WHERE warning_id = #{warningId}")
    int updateChatMessageId(@Param("warningId") Long warningId, @Param("chatMessageId") Long chatMessageId);
}

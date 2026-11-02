package com.example.bishe.mapper;

import com.example.bishe.entity.ChatViolation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChatViolationMapper {

    @Insert("INSERT INTO chat_violation(message_id, word_id, matched_word, sender_id, receiver_id, is_private, " +
            "group_id, content_snapshot, status) " +
            "VALUES(#{messageId}, #{wordId}, #{matchedWord}, #{senderId}, #{receiverId}, #{isPrivate}, " +
            "#{groupId}, #{contentSnapshot}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "violationId")
    int insert(ChatViolation violation);

    @Select("SELECT * FROM chat_violation WHERE violation_id = #{violationId}")
    ChatViolation findById(Long violationId);

    @Select("<script>"
            + "SELECT * FROM chat_violation"
            + "<where>"
            + "  <if test='status != null'> AND status = #{status}</if>"
            + "</where>"
            + " ORDER BY hit_time DESC, violation_id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<ChatViolation> findPage(@Param("status") Integer status,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    @Select("<script>"
            + "SELECT COUNT(*) FROM chat_violation"
            + "<where>"
            + "  <if test='status != null'> AND status = #{status}</if>"
            + "</where>"
            + "</script>")
    int countPage(@Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM chat_violation WHERE status = 0")
    int countPending();

    @Update("UPDATE chat_violation SET status = #{status}, handled_by = #{handledBy}, " +
            "handled_at = NOW(), handle_note = #{handleNote} WHERE violation_id = #{violationId}")
    int updateHandle(@Param("violationId") Long violationId,
                     @Param("status") int status,
                     @Param("handledBy") String handledBy,
                     @Param("handleNote") String handleNote);
}

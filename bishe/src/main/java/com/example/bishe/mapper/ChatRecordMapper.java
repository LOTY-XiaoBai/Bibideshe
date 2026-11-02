package com.example.bishe.mapper;

import com.example.bishe.entity.ChatRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 聊天记录表数据访问接口 (表6 chat_record)。
 */
@Mapper
public interface ChatRecordMapper {

    @Insert("INSERT INTO chat_record(sender_id, receiver_id, is_private, group_id, content, message_type, reference_id, is_deleted) " +
            "VALUES(#{senderId}, #{receiverId}, #{isPrivate}, #{groupId}, #{content}, #{messageType}, #{referenceId}, #{isDeleted})")
    @Options(useGeneratedKeys = true, keyProperty = "messageId")
    int insert(ChatRecord record);

    @Select("SELECT * FROM chat_record WHERE message_id = #{messageId}")
    ChatRecord findById(Long messageId);

    /**
     * 私聊历史(双方可见未删除)。
     * 按 (sender,receiver) 双向匹配。
     */
    @Select("SELECT * FROM chat_record WHERE is_private = 1 AND is_deleted = 0 " +
            "AND ((sender_id = #{userId} AND receiver_id = #{friendId}) " +
            "  OR (sender_id = #{friendId} AND receiver_id = #{userId})) " +
            "ORDER BY send_time ASC, message_id ASC")
    List<ChatRecord> findPrivateHistory(@Param("userId") Long userId, @Param("friendId") Long friendId);

    @Select("SELECT * FROM chat_record WHERE is_private = 0 AND group_id = #{groupId} AND is_deleted = 0 " +
            "ORDER BY send_time ASC, message_id ASC")
    List<ChatRecord> findGroupHistory(Long groupId);

    /** 管理员查看全部(含软删除), 可按私聊/群聊筛选 */
    @Select("<script>"
            + "SELECT * FROM chat_record"
            + "<where>"
            + "  <if test='isPrivate != null'> AND is_private = #{isPrivate}</if>"
            + "</where>"
            + " ORDER BY send_time DESC, message_id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<ChatRecord> findAllForAdmin(@Param("isPrivate") Integer isPrivate,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    @Select("<script>"
            + "SELECT COUNT(*) FROM chat_record"
            + "<where>"
            + "  <if test='isPrivate != null'> AND is_private = #{isPrivate}</if>"
            + "</where>"
            + "</script>")
    int countAll(@Param("isPrivate") Integer isPrivate);

    @Select("SELECT * FROM chat_record WHERE group_id = #{groupId} AND is_private = 0 " +
            "ORDER BY send_time DESC, message_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ChatRecord> findByGroupForAdmin(@Param("groupId") Long groupId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    /** 管理员查看双方私聊(含软删除) */
    @Select("SELECT * FROM chat_record WHERE is_private = 1 " +
            "AND ((sender_id = #{userA} AND receiver_id = #{userB}) " +
            "  OR (sender_id = #{userB} AND receiver_id = #{userA})) " +
            "ORDER BY send_time ASC, message_id ASC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<ChatRecord> findPrivateForAdmin(@Param("userA") Long userA,
                                         @Param("userB") Long userB,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /** 系统通知私聊(发送人ID=0) */
    @Select("SELECT * FROM chat_record WHERE is_private = 1 AND is_deleted = 0 " +
            "AND ((sender_id = 0 AND receiver_id = #{userId}) OR (sender_id = #{userId} AND receiver_id = 0)) " +
            "ORDER BY send_time ASC, message_id ASC")
    List<ChatRecord> findSystemNotifyHistory(Long userId);

    @Update("UPDATE chat_record SET is_deleted = 1 WHERE message_id = #{messageId}")
    int softDelete(Long messageId);

    @Update("UPDATE chat_record SET content = #{content} WHERE message_id = #{messageId}")
    int updateContent(@Param("messageId") Long messageId, @Param("content") String content);
}

package com.example.bishe.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天记录实体 (表6 chat_record)。
 * 私聊时 isPrivate=1, groupId=0, receiverId 为对方;
 * 群聊时 isPrivate=0, groupId 为群号, receiverId=0。
 * isDeleted=1 为软删除(撤回), 管理员仍可查看。
 */
@Data
public class ChatRecord {

    private Long messageId;

    private LocalDateTime sendTime;

    /** 发送人ID */
    private Long senderId;

    /**
     * 接收人ID(私聊用; 群聊为 0)。
     * 注意: 需要在数据库 chat_record 表添加 receiver_id 字段, 见最终 SQL 说明。
     */
    private Long receiverId;

    /** 是否私聊: 1是, 0群聊 */
    private Integer isPrivate;

    /** 群聊群号(私聊为 0) */
    private Long groupId;

    private String content;

    /** 消息类型: 0文本, 1图片 */
    private Integer messageType;

    /** 被引用消息ID(没有为 0) */
    private Long referenceId;

    /** 软删除标记: 0正常, 1已撤回 */
    private Integer isDeleted;
}

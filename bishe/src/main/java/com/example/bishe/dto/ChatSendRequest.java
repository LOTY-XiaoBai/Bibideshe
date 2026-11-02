package com.example.bishe.dto;

import lombok.Data;

/**
 * 发送消息请求(私聊/群聊共用部分字段)。
 */
@Data
public class ChatSendRequest {

    /** 私聊对方ID(私聊必填) */
    private Long receiverId;

    /** 群号(群聊必填) */
    private Long groupId;

    private String content;

    /** 消息类型: 0文本, 1图片, 默认0 */
    private Integer messageType;

    /** 引用的消息ID, 默认0 */
    private Long referenceId;
}

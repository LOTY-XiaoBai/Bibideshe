package com.example.bishe.dto;

import lombok.Data;

/**
 * 撤回消息请求。
 */
@Data
public class ChatRecallRequest {

    private Long messageId;
}

package com.example.bishe.dto;

import lombok.Data;

/**
 * 撤回/修改消息请求。
 */
@Data
public class ChatMessageUpdateRequest {

    private Long messageId;
    /** 修改内容时填写; 软删除/撤回可不填 */
    private String content;
}

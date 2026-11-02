package com.example.bishe.dto;

import lombok.Data;

/**
 * 仅需群号的操作请求(加入/退出/解散)。
 */
@Data
public class GroupIdRequest {

    private Long groupId;
}

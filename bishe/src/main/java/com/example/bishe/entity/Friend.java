package com.example.bishe.entity;

import lombok.Data;

/**
 * 好友关系实体 (表5 friend)。
 * 入库时约定 friendAId &lt; friendBId, 避免双向重复。
 */
@Data
public class Friend {

    /** 好友AID(较小的用户ID) */
    private Long friendAId;

    /** 好友BID(较大的用户ID) */
    private Long friendBId;
}

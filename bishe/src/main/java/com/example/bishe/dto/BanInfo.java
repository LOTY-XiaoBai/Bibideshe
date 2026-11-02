package com.example.bishe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 永久封禁提示信息(登录/被踢出时返回给前端弹窗)。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BanInfo {

    public static final String TYPE = "ACCOUNT_BANNED";

    /** 固定标识, 前端据此弹出封禁窗口 */
    private String type = TYPE;

    /** 封禁理由 */
    private String banReason;

    /** 申诉联系 QQ */
    private String adminQq = "3088184559";

    public static BanInfo of(String banReason) {
        BanInfo info = new BanInfo();
        info.setType(TYPE);
        info.setBanReason(banReason == null || banReason.isBlank() ? "违规" : banReason.trim());
        info.setAdminQq("3088184559");
        return info;
    }
}

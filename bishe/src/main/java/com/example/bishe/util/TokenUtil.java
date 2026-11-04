package com.example.bishe.util;

import com.example.bishe.common.AuthUser;
import com.example.bishe.common.BusinessException;
import com.example.bishe.common.UserRole;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

/**
 * 简易 Token 工具: payload.signature (HMAC-SHA256)。
 * 格式: base64url(userId|role|subject|expireAt|tokenId).base64url(hmac)
 */
public final class TokenUtil {

    private static final String HMAC_ALG = "HmacSHA256";
    /** Token 有效期: 2 小时 */
    private static final long EXPIRE_MILLIS = 2L * 60 * 60 * 1000;
    private static volatile String configuredSecret;

    private TokenUtil() {
    }

    public static void configure(String secret) {
        if (secret == null || secret.isBlank() || secret.trim().length() < 32) {
            throw new IllegalStateException("TOKEN_SECRET 必须设置且不少于32个字符");
        }
        configuredSecret = secret.trim();
    }

    private static String secret() {
        if (configuredSecret != null) {
            return configuredSecret;
        }
        String fromEnv = System.getenv("TOKEN_SECRET");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromProp = System.getProperty("app.token.secret");
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp.trim();
        }
        throw new IllegalStateException("TOKEN_SECRET 未配置，拒绝启动不安全的签名逻辑");
    }

    public static String createToken(AuthUser user) {
        long expireAt = System.currentTimeMillis() + EXPIRE_MILLIS;
        String tokenId = UUID.randomUUID().toString();
        String userIdPart = user.getUserId() == null ? "" : String.valueOf(user.getUserId());
        String payload = userIdPart + "|" + user.getRole() + "|" + user.getSubject() + "|" + expireAt + "|" + tokenId;
        String payloadEncoded = base64UrlEncode(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(payloadEncoded);
        return payloadEncoded + "." + signature;
    }

    public static AuthUser parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "未登录或登录已失效, 请重新登录");
        }
        String raw = token.trim();
        if (raw.regionMatches(true, 0, "Bearer ", 0, 7)) {
            raw = raw.substring(7).trim();
        }
        String[] parts = raw.split("\\.");
        if (parts.length != 2) {
            throw new BusinessException(401, "无效的登录凭证");
        }
        String payloadEncoded = parts[0];
        String signature = parts[1];
        byte[] expected = sign(payloadEncoded).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(401, "登录凭证校验失败, 请重新登录");
        }
        String payload = new String(base64UrlDecode(payloadEncoded), StandardCharsets.UTF_8);
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 5 || fields[4].isBlank()) {
            throw new BusinessException(401, "登录凭证格式错误");
        }
        long expireAt;
        try {
            expireAt = Long.parseLong(fields[3]);
        } catch (NumberFormatException e) {
            throw new BusinessException(401, "登录凭证格式错误");
        }
        if (System.currentTimeMillis() > expireAt) {
            throw new BusinessException(401, "登录已过期, 请重新登录");
        }
        Long userId = null;
        if (fields[0] != null && !fields[0].isEmpty()) {
            try {
                userId = Long.parseLong(fields[0]);
            } catch (NumberFormatException e) {
                throw new BusinessException(401, "登录凭证格式错误");
            }
        }
        String role = fields[1];
        if (!UserRole.STUDENT.equals(role) && !UserRole.TEACHER.equals(role) && !UserRole.ADMIN.equals(role) && !UserRole.MERCHANT.equals(role)) {
            throw new BusinessException(401, "登录凭证角色无效");
        }
        return new AuthUser(userId, fields[2], role, fields[4], expireAt);
    }

    private static String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            return base64UrlEncode(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Token 签名失败", e);
        }
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] base64UrlDecode(String text) {
        return Base64.getUrlDecoder().decode(text);
    }
}

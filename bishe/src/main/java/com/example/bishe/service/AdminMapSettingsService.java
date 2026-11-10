package com.example.bishe.service;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Service
public class AdminMapSettingsService {
    private static final String CONFIG_ROW = " FROM initial_admin_config WHERE id=1";
    private static final int NONCE_BYTES = 12;

    private final JdbcTemplate db;
    private final AdminOperationService operations;
    private final String legacyAdminId;
    private final String deploymentKey;
    private final String encryptionSecret;
    private final SecureRandom random = new SecureRandom();

    public AdminMapSettingsService(JdbcTemplate db, AdminOperationService operations,
                                   @Value("${app.admin.initial-id:}") String legacyAdminId,
                                   @Value("${app.merchant.amap-key:}") String deploymentKey,
                                   @Value("${app.admin.settings-secret:}") String encryptionSecret,
                                   @Value("${app.token.secret:}") String tokenSecret) {
        this.db = db;
        this.operations = operations;
        this.legacyAdminId = legacyAdminId.trim();
        this.deploymentKey = deploymentKey.trim();
        this.encryptionSecret = (encryptionSecret.isBlank() ? tokenSecret : encryptionSecret).trim();
    }

    public Map<String, Object> status() {
        String actor = AuthContext.requireAdminId();
        String owner = db.queryForObject("SELECT admin_id" + CONFIG_ROW, String.class);
        if (!ownsSettings(actor, owner)) return Map.of("editable", false);
        String ciphertext = db.queryForObject("SELECT amap_key_ciphertext" + CONFIG_ROW, String.class);
        return Map.of("editable", true, "configured", ciphertext != null || !deploymentKey.isBlank());
    }

    @Transactional
    public Map<String, Object> save(String key) {
        String actor = AuthContext.requireAdminId();
        String owner = db.queryForObject("SELECT admin_id" + CONFIG_ROW + " FOR UPDATE", String.class);
        if (!ownsSettings(actor, owner)) throw new BusinessException(403, "仅初代管理员可以修改地图配置");
        if (key == null || (!key.isBlank() && !key.trim().matches("[a-zA-Z0-9_-]{1,256}"))) {
            throw new BusinessException("请输入有效的高德 Web 服务 Key");
        }
        String value = key.trim();
        db.update("UPDATE initial_admin_config SET admin_id=?, amap_key_ciphertext=? WHERE id=1",
                actor, value.isEmpty() ? null : encrypt(value));
        operations.record("修改地图配置", "SYSTEM", "amap", "", null, "", value.isEmpty() ? "恢复部署配置" : "更新地图密钥");
        return Map.of("editable", true, "configured", !value.isEmpty() || !deploymentKey.isEmpty());
    }

    String effectiveKey() {
        String value = db.queryForObject("SELECT amap_key_ciphertext" + CONFIG_ROW, String.class);
        return value == null ? deploymentKey : decrypt(value);
    }

    private boolean ownsSettings(String actor, String owner) {
        return actor.equals(owner == null ? legacyAdminId : owner);
    }

    private String encrypt(String value) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] ciphertext = transform(Cipher.ENCRYPT_MODE, nonce, value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                .put(nonce).put(ciphertext).array());
    }

    private String decrypt(String value) {
        var encoded = ByteBuffer.wrap(Base64.getDecoder().decode(value));
        byte[] nonce = new byte[NONCE_BYTES];
        encoded.get(nonce);
        byte[] ciphertext = new byte[encoded.remaining()];
        encoded.get(ciphertext);
        return new String(transform(Cipher.DECRYPT_MODE, nonce, ciphertext), StandardCharsets.UTF_8);
    }

    private byte[] transform(int mode, byte[] nonce, byte[] value) {
        if (encryptionSecret.length() < 32) {
            throw new BusinessException(503, "地图配置的部署加密密钥未就绪");
        }
        try {
            byte[] key = MessageDigest.getInstance("SHA-256")
                    .digest(("bishe:amap:" + encryptionSecret).getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return cipher.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(503, "地图配置暂不可用");
        }
    }
}

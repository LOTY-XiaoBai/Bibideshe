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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class AdminMapSettingsService {
    public record CredentialInput(String type, String label, String key, String securityCode) {}
    public record CredentialSummary(long id, String type, String label, String maskedKey) {}
    public record JsCredential(long id, String key, String securityCode) {}
    private record CredentialRow(long id, String type, String label, String ciphertext, String security) {}

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
        return editableStatus();
    }

    @Transactional
    public Map<String, Object> save(String key) {
        String actor = lockOwner();
        if (key == null || (!key.isBlank() && !key.trim().matches("[a-zA-Z0-9_-]{1,256}"))) {
            throw new BusinessException("请输入有效的高德 Web 服务 Key");
        }
        String value = key.trim();
        db.update("UPDATE initial_admin_config SET admin_id=?, amap_key_ciphertext=? WHERE id=1",
                actor, value.isEmpty() ? null : encrypt(value));
        operations.record("修改地图配置", "SYSTEM", "amap", "", null, "", value.isEmpty() ? "恢复部署配置" : "更新地图密钥");
        return editableStatus();
    }

    @Transactional
    public Map<String, Object> addCredential(CredentialInput input) {
        String actor = lockOwner();
        requireType(input.type());
        String label = input.label() == null ? "" : input.label().trim();
        if (label.isEmpty() || label.length() > 80) throw new BusinessException("请输入 1 至 80 字的凭据名称");
        String key = validCredential(input.key(), "请输入有效的高德 Key");
        String security = "JS_API".equals(input.type())
                ? encrypt(validCredential(input.securityCode(), "请输入该 Web 端 Key 对应的安全密钥")) : null;
        var rows = credentials(input.type());
        if (rows.stream().anyMatch(row -> key.equals(decrypt(row.ciphertext())))) {
            throw new BusinessException("该类型下已配置此 Key");
        }
        int priority = db.queryForObject("SELECT COALESCE(MAX(priority), -1)+1 FROM amap_credential WHERE type=?",
                Integer.class, input.type());
        db.update("INSERT INTO amap_credential(type,label,priority,key_ciphertext,security_ciphertext) VALUES(?,?,?,?,?)",
                input.type(), label, priority, encrypt(key), security);
        db.update("UPDATE initial_admin_config SET admin_id=? WHERE id=1", actor);
        operations.record("添加地图凭据", "SYSTEM", "amap", "", null, "", input.type());
        return editableStatus();
    }

    @Transactional
    public Map<String, Object> orderCredentials(String type, List<Long> ids) {
        lockOwner();
        requireType(type);
        List<Long> existing = credentials(type).stream().map(CredentialRow::id).toList();
        if (ids == null || existing.size() != ids.size() || !existing.containsAll(ids)
                || new LinkedHashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(409, "地图配置已变更，请刷新后重试");
        }
        for (int i = 0; i < ids.size(); i++) {
            db.update("UPDATE amap_credential SET priority=? WHERE id=?", i, ids.get(i));
        }
        operations.record("调整地图凭据顺序", "SYSTEM", "amap", "", null, "", type);
        return editableStatus();
    }

    @Transactional
    public Map<String, Object> deleteCredential(long id) {
        lockOwner();
        if (db.update("DELETE FROM amap_credential WHERE id=?", id) == 0) {
            throw new BusinessException(404, "该地图凭据已删除");
        }
        operations.record("删除地图凭据", "SYSTEM", id, "", null, "", "");
        return editableStatus();
    }

    List<String> effectiveWebKeys() {
        var keys = new LinkedHashSet<String>();
        for (var row : credentials("WEB_SERVICE")) keys.add(decrypt(row.ciphertext()));
        String value = db.queryForObject("SELECT amap_key_ciphertext" + CONFIG_ROW, String.class);
        if (value != null) keys.add(decrypt(value));
        if (!deploymentKey.isBlank()) keys.add(deploymentKey);
        return List.copyOf(keys);
    }

    public JsCredential primaryJsCredential() {
        return credentials("JS_API").stream().findFirst().map(this::jsCredential)
                .orElseThrow(() -> new BusinessException(503, "请联系管理员配置高德 Web 端 Key 和安全密钥"));
    }

    public JsCredential jsCredential(long id) {
        return credentials("JS_API").stream().filter(row -> row.id() == id).findFirst().map(this::jsCredential)
                .orElseThrow(() -> new BusinessException(403, "地图凭据已删除，请重新打开地图"));
    }

    private JsCredential jsCredential(CredentialRow row) {
        return new JsCredential(row.id(), decrypt(row.ciphertext()), decrypt(row.security()));
    }

    private Map<String, Object> editableStatus() {
        List<CredentialSummary> summaries = db.query("SELECT id,type,label,key_ciphertext FROM amap_credential ORDER BY type,priority,id",
                (rs, index) -> new CredentialSummary(rs.getLong("id"), rs.getString("type"), rs.getString("label"),
                        mask(decrypt(rs.getString("key_ciphertext")))));
        String legacy = db.queryForObject("SELECT amap_key_ciphertext" + CONFIG_ROW, String.class);
        return Map.of("editable", true, "credentials", summaries,
                "legacyConfigured", legacy != null, "deploymentConfigured", !deploymentKey.isBlank(),
                "configured", legacy != null || !deploymentKey.isBlank()
                        || summaries.stream().anyMatch(row -> "WEB_SERVICE".equals(row.type())));
    }

    private List<CredentialRow> credentials(String type) {
        return db.query("SELECT id,type,label,key_ciphertext,security_ciphertext FROM amap_credential WHERE type=? ORDER BY priority,id",
                (rs, index) -> new CredentialRow(rs.getLong("id"), rs.getString("type"), rs.getString("label"),
                        rs.getString("key_ciphertext"), rs.getString("security_ciphertext")), type);
    }

    private String lockOwner() {
        String actor = AuthContext.requireAdminId();
        String owner = db.queryForObject("SELECT admin_id" + CONFIG_ROW + " FOR UPDATE", String.class);
        if (!ownsSettings(actor, owner)) throw new BusinessException(403, "仅初代管理员可以修改地图配置");
        return actor;
    }

    private void requireType(String type) {
        if (!"WEB_SERVICE".equals(type) && !"JS_API".equals(type)) throw new BusinessException("请选择高德 Key 类型");
    }

    private String validCredential(String input, String error) {
        if (input == null || !input.trim().matches("[a-zA-Z0-9_-]{1,256}")) throw new BusinessException(error);
        return input.trim();
    }

    private String mask(String key) {
        return key.length() <= 4 ? "••••" : "••••" + key.substring(key.length() - 4);
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

package com.example.bishe.service;

import com.example.bishe.common.AuthUser;
import com.example.bishe.common.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Redis 保存已注销凭证，使退出登录在服务端立即生效。 */
@Service
public class AuthSessionService {

    private static final String REVOKED_PREFIX = "auth:revoked:";
    private final StringRedisTemplate redisTemplate;

    public AuthSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void assertActive(AuthUser user) {
        if (user == null || user.getTokenId() == null
                || Boolean.TRUE.equals(redisTemplate.hasKey(REVOKED_PREFIX + user.getTokenId()))) {
            throw new BusinessException(401, "登录已注销或已失效，请重新登录");
        }
    }

    public void revoke(AuthUser user) {
        if (user == null || user.getTokenId() == null || user.getExpireAt() == null) {
            return;
        }
        long ttlMillis = Math.max(1, user.getExpireAt() - System.currentTimeMillis());
        redisTemplate.opsForValue().set(REVOKED_PREFIX + user.getTokenId(), "1", Duration.ofMillis(ttlMillis));
    }
}

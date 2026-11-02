package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 登录失败限流: 连续输错 6 次触发临时锁定, 时长按档位递增。
 * 状态存 Redis, 锁定键带 TTL, 服务重启后仍生效。
 * 对外仅提示「错误次数太多, 请稍后再试」, 不暴露临时封禁概念。
 * <p>
 * 档位: 3 分钟 → 10 分钟 → 1 小时 → 24 小时
 */
@Service
public class LoginGuardService {

    private static final int FAIL_THRESHOLD = 6;
    private static final long[] LOCK_MILLIS = {
            3 * 60 * 1000L,
            10 * 60 * 1000L,
            60 * 60 * 1000L,
            24 * 60 * 60 * 1000L
    };
    private static final String LOCK_MESSAGE = "错误次数太多，请稍后再试";
    private static final String FAIL_PREFIX = "login:fail:";
    private static final String LOCK_PREFIX = "login:lock:";
    private static final String LEVEL_PREFIX = "login:level:";

    private final StringRedisTemplate redisTemplate;

    public LoginGuardService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void assertNotLocked(String accountId) {
        String key = normalize(accountId);
        if (key == null) {
            return;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_PREFIX + key))) {
            throw new BusinessException(429, LOCK_MESSAGE);
        }
    }

    public void onPasswordWrong(String accountId) {
        String key = normalize(accountId);
        if (key == null) {
            return;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_PREFIX + key))) {
            throw new BusinessException(429, LOCK_MESSAGE);
        }

        Long failCount = redisTemplate.opsForValue().increment(FAIL_PREFIX + key);
        if (failCount != null && failCount == 1L) {
            // 失败计数窗口与最长锁定同级, 避免永久残留
            redisTemplate.expire(FAIL_PREFIX + key, Duration.ofDays(2));
        }
        if (failCount != null && failCount >= FAIL_THRESHOLD) {
            int level = readLevel(key);
            int levelIndex = Math.min(level, LOCK_MILLIS.length - 1);
            long lockMillis = LOCK_MILLIS[levelIndex];
            redisTemplate.opsForValue().set(LOCK_PREFIX + key, "1", lockMillis, TimeUnit.MILLISECONDS);
            redisTemplate.opsForValue().set(LEVEL_PREFIX + key, String.valueOf(Math.min(level + 1, LOCK_MILLIS.length)));
            redisTemplate.delete(FAIL_PREFIX + key);
            throw new BusinessException(429, LOCK_MESSAGE);
        }
        throw new BusinessException("密码错误");
    }

    public void onLoginSuccess(String accountId) {
        String key = normalize(accountId);
        if (key == null) {
            return;
        }
        redisTemplate.delete(FAIL_PREFIX + key);
        redisTemplate.delete(LOCK_PREFIX + key);
        redisTemplate.delete(LEVEL_PREFIX + key);
    }

    private int readLevel(String key) {
        String raw = redisTemplate.opsForValue().get(LEVEL_PREFIX + key);
        if (!StringUtils.hasText(raw)) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String normalize(String accountId) {
        if (!StringUtils.hasText(accountId)) {
            return null;
        }
        return accountId.trim().toLowerCase();
    }
}

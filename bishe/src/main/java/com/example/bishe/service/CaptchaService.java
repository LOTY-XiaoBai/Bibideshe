package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 图形验证码: easy-captcha 生成, Redis 存储, 5 分钟过期, 一次性校验。
 */
@Service
public class CaptchaService {

    private static final String KEY_PREFIX = "captcha:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    /** 仅用于自动化测试: 为 true 时接口额外返回明文 captchaCode。生产环境务必保持 false。 */
    @Value("${app.captcha.expose-code:false}")
    private boolean exposeCode;

    public CaptchaService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Map<String, String> create() {
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        captcha.setCharType(Captcha.TYPE_DEFAULT);
        String key = UUID.randomUUID().toString().replace("-", "");
        String code = captcha.text().toLowerCase();
        redisTemplate.opsForValue().set(KEY_PREFIX + key, code, TTL);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("captchaKey", key);
        result.put("captchaImage", captcha.toBase64());
        if (exposeCode) {
            result.put("captchaCode", code);
        }
        return result;
    }

    public void verify(String captchaKey, String captchaCode) {
        if (!StringUtils.hasText(captchaKey) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException("请输入验证码");
        }
        String redisKey = KEY_PREFIX + captchaKey.trim();
        String code = redisTemplate.opsForValue().getAndDelete(redisKey);
        if (code == null) {
            throw new BusinessException("验证码已过期, 请刷新后重试");
        }
        if (!code.equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException("验证码错误");
        }
    }
}

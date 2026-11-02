package com.example.bishe.config;

import com.example.bishe.util.TokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** 启动时校验 Token 密钥，禁止使用代码内置默认密钥。 */
@Configuration
public class TokenSecurityConfig {

    public TokenSecurityConfig(@Value("${app.token.secret:}") String secret) {
        TokenUtil.configure(secret);
    }
}

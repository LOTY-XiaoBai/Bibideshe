package com.example.bishe.util;

import com.example.bishe.common.BusinessException;

/** BCrypt 入参长度边界与基础密码强度策略。 */
public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException("密码不少于8个字符");
        }
        if (password.length() > 72) {
            throw new BusinessException("密码不能超过72个字符");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new BusinessException("密码至少包含字母和数字");
        }
    }
}

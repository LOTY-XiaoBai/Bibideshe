package com.example.bishe.config;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.AuthUser;
import com.example.bishe.common.BusinessException;
import com.example.bishe.common.UserRole;
import com.example.bishe.dto.BanInfo;
import com.example.bishe.service.ModerationService;
import com.example.bishe.service.AuthSessionService;
import com.example.bishe.util.TokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录鉴权拦截器: 校验 Authorization Bearer Token, 并将用户写入 AuthContext。
 * 师生账号额外校验永久封禁状态, 已封禁则拒绝并返回封禁信息。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final ModerationService moderationService;
    private final JsonMapper jsonMapper;
    private final AuthSessionService authSessionService;

    public AuthInterceptor(ModerationService moderationService, JsonMapper jsonMapper,
                           AuthSessionService authSessionService) {
        this.moderationService = moderationService;
        this.jsonMapper = jsonMapper;
        this.authSessionService = authSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        try {
            AuthUser user = TokenUtil.parseToken(authorization);
            authSessionService.assertActive(user);
            if (UserRole.STUDENT.equals(user.getRole()) || UserRole.TEACHER.equals(user.getRole())) {
                try {
                    moderationService.assertAccountActive(user.getUserId());
                } catch (BusinessException e) {
                    if (e.getCode() == 403 && e.getData() instanceof BanInfo) {
                        writeBanned(response, e.getMessage(), (BanInfo) e.getData());
                        return false;
                    }
                    throw e;
                }
            }
            AuthContext.set(user);
            return true;
        } catch (BusinessException e) {
            if (e.getCode() == 403 && e.getData() instanceof BanInfo) {
                writeBanned(response, e.getMessage(), (BanInfo) e.getData());
                return false;
            }
            writeUnauthorized(response, e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new HashMap<>();
        body.put("code", 401);
        body.put("message", message == null ? "未登录或登录已失效" : message);
        body.put("data", null);
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }

    private void writeBanned(HttpServletResponse response, String message, BanInfo banInfo) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new HashMap<>();
        body.put("code", 403);
        body.put("message", message == null ? "该账号已经被永久封禁" : message);
        body.put("data", banInfo);
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}

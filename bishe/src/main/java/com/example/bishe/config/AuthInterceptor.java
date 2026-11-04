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
    private final com.example.bishe.service.MerchantService merchants;

    public AuthInterceptor(ModerationService moderationService, JsonMapper jsonMapper,
                           AuthSessionService authSessionService, com.example.bishe.service.MerchantService merchants) {
        this.moderationService = moderationService;
        this.jsonMapper = jsonMapper;
        this.authSessionService = authSessionService;
        this.merchants = merchants;
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
            if (UserRole.MERCHANT.equals(user.getRole())) {
                boolean onboarding = merchants.assertSession(user);
                String path = request.getRequestURI().substring(request.getContextPath().length());
                boolean profile = path.equals("/api/merchant/profile") || path.equals("/api/merchant/location/city") || path.equals("/api/merchant/location/available") || path.equals("/api/auth/logout");
                boolean business = path.startsWith("/api/merchant/") || path.equals("/api/file/upload/image");
                if ((!profile && !business) || (onboarding && !profile)) {
                    response.setStatus(403);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(jsonMapper.writeValueAsString(Map.of("code",403,"message",onboarding?"请先修改初始密码并完善发货城市":"商家账号仅可访问自己的商家工作台")));
                    return false;
                }
            }
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
            if (e.getCode()==403 && e.getData() instanceof Map<?,?> data && "MERCHANT_BANNED".equals(data.get("type"))) {
                response.setStatus(403);response.setCharacterEncoding(StandardCharsets.UTF_8.name());response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(jsonMapper.writeValueAsString(Map.of("code",403,"message",e.getMessage(),"data",e.getData())));return false;
            }
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

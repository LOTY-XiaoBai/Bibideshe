package com.example.bishe.controller;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.BusinessException;
import com.example.bishe.common.Result;
import com.example.bishe.dto.CheckInBounds;
import com.example.bishe.service.AdminMapSettingsService;
import com.example.bishe.service.CheckInService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

@RestController
@RequestMapping("/api/location")
public class AmapJsController {
    private static final String PROXY_PATH = "/api/location/_AMapService";
    private static final String COOKIE = "amap_map";
    private static final String REDIS_PREFIX = "amap:map:";
    private static final Duration SESSION_DURATION = Duration.ofMinutes(30);

    private final AdminMapSettingsService settings;
    private final CheckInService checkIns;
    private final StringRedisTemplate redis;
    private final String restBaseUrl;
    private final String jsBaseUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public AmapJsController(AdminMapSettingsService settings, CheckInService checkIns, StringRedisTemplate redis,
                            @Value("${app.amap.rest-base-url:https://restapi.amap.com}") String restBaseUrl,
                            @Value("${app.amap.js-base-url:https://webapi.amap.com}") String jsBaseUrl) {
        this.settings = settings;
        this.checkIns = checkIns;
        this.redis = redis;
        this.restBaseUrl = restBaseUrl;
        this.jsBaseUrl = jsBaseUrl;
    }

    @GetMapping("/map-config")
    public Result<Map<String, Object>> config(@RequestParam(required = false) Long checkInId,
            HttpServletRequest request, HttpServletResponse response) {
        CheckInBounds bounds = null;
        if (checkInId == null) AuthContext.requireTeacherId();
        else bounds = checkIns.locationBoundsForStudent(checkInId, AuthContext.requireStudentId());
        var credential = settings.primaryJsCredential();
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(REDIS_PREFIX + ticket, Long.toString(credential.id()), SESSION_DURATION);
        Cookie cookie = new Cookie(COOKIE, ticket);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath(PROXY_PATH);
        cookie.setMaxAge((int) SESSION_DURATION.toSeconds());
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
        response.setHeader("Cache-Control", "no-store");
        return Result.success(bounds == null ? Map.of("key", credential.key(), "serviceHost", PROXY_PATH)
                : Map.of("key", credential.key(), "serviceHost", PROXY_PATH, "bounds", bounds));
    }

    @GetMapping("/_AMapService/v3/ip")
    public ResponseEntity<byte[]> ip(@CookieValue(name = COOKIE, required = false) String ticket,
                                      @RequestParam MultiValueMap<String, String> parameters, HttpServletRequest request) {
        return forward(restBaseUrl + "/v3/ip", ticket, parameters, request);
    }

    @GetMapping("/_AMapService/v4/map/styles")
    public ResponseEntity<byte[]> styles(@CookieValue(name = COOKIE, required = false) String ticket,
                                          @RequestParam MultiValueMap<String, String> parameters, HttpServletRequest request) {
        return forward(jsBaseUrl + "/v4/map/styles", ticket, parameters, request);
    }

    private ResponseEntity<byte[]> forward(String upstream, String ticket, MultiValueMap<String, String> parameters,
                                            HttpServletRequest request) {
        if (ticket == null || !ticket.matches("[a-f0-9-]{36}")) {
            throw new BusinessException(403, "请从签到页面打开地图");
        }
        String id = redis.opsForValue().get(REDIS_PREFIX + ticket);
        if (id == null) throw new BusinessException(403, "地图授权已过期，请重新打开地图");
        var credential = settings.jsCredential(Long.parseLong(id));
        String suppliedKey = parameters.getFirst("key");
        if (suppliedKey != null && !credential.key().equals(suppliedKey)) {
            throw new BusinessException(403, "地图配置已变更，请重新打开地图");
        }
        String callback = parameters.getFirst("callback");
        if (callback != null && !callback.matches("[a-zA-Z_$][a-zA-Z0-9_$.]{0,95}")) {
            throw new BusinessException("地图回调参数无效");
        }
        var query = new StringJoiner("&");
        parameters.forEach((name, values) -> {
            if (!"key".equalsIgnoreCase(name) && !"jscode".equalsIgnoreCase(name)
                    && !"callback".equalsIgnoreCase(name)) {
                values.forEach(value -> query.add(encode(name) + "=" + encode(value)));
            }
        });
        if (callback != null) query.add("callback=" + encode(callback));
        query.add("key=" + encode(credential.key())).add("jscode=" + encode(credential.securityCode()));
        if (query.length() > 8192) throw new BusinessException("地图请求参数过长");
        var outgoing = HttpRequest.newBuilder(URI.create(upstream + "?" + query)).timeout(Duration.ofSeconds(5)).GET();
        String referer = request.getHeader("Referer");
        if (referer != null) outgoing.header("Referer", referer);
        try {
            var result = client.send(outgoing.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (result.statusCode() != 200) throw new BusinessException(502, "高德地图暂不可用");
            return ResponseEntity.ok().header("Cache-Control", "no-store")
                    .header("Content-Type", callback == null ? "application/json" : "application/javascript")
                    .body(result.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "高德地图暂不可用");
        } catch (IOException e) {
            throw new BusinessException(502, "高德地图暂不可用");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

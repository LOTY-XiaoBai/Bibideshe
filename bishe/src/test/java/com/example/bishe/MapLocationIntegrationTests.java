package com.example.bishe;

import com.example.bishe.common.AuthUser;
import com.example.bishe.common.UserRole;
import com.example.bishe.util.TokenUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@SpringBootTest(properties = {"app.token.secret=test-token-secret-with-at-least-32-characters",
        "app.admin.initial-id=map-owner", "app.merchant.amap-key=deployment-map-key"})
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION", matches = "true")
@Transactional
class MapLocationIntegrationTests {
    private static HttpServer amap;
    @Autowired WebApplicationContext web;

    @DynamicPropertySource
    static void mockMap(DynamicPropertyRegistry properties) throws IOException {
        amap = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        amap.createContext("/v3/assistant/coordinate/convert", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            reply(exchange, query.contains("key=limited-service-key")
                    ? "{\"status\":\"0\",\"infocode\":\"10003\"}"
                    : "{\"status\":\"1\",\"locations\":\"116.30,39.80\"}");
        });
        amap.createContext("/v3/geocode/regeo", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String address = query.contains("key=backup-service-key") ? "备用地址"
                    : query.contains("key=legacy-service-key") ? "旧配置地址" : "部署配置地址";
            reply(exchange, "{\"status\":\"1\",\"regeocode\":{\"formatted_address\":\"" + address + "\"}}");
        });
        amap.createContext("/v3/ip", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            boolean first = query.contains("key=first-js-key") && query.contains("jscode=first-js-secret");
            boolean second = query.contains("key=second-js-key") && query.contains("jscode=second-js-secret");
            reply(exchange, first || second ? "{\"status\":\"1\"}" : "{\"status\":\"0\"}");
        });
        amap.start();
        properties.add("app.amap.rest-base-url", () -> "http://127.0.0.1:" + amap.getAddress().getPort());
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, String response) throws IOException {
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    @AfterAll static void stopMap() { if (amap != null) amap.stop(0); }

    @Test void addressUsesTheBackupKeyWhenThePrimaryQuotaIsExhausted() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(web).build();
        String owner = bearer(null, "map-owner", UserRole.ADMIN);
        String otherAdmin = bearer(null, "map-other", UserRole.ADMIN);
        String student = bearer(8900000000001L, "map-student", UserRole.STUDENT);
        var address = post("/api/location/address").header("Authorization", student)
                .contentType("application/json").content("{\"latitude\":39.8,\"longitude\":116.3}");
        mvc.perform(address).andExpect(status().isOk()).andExpect(jsonPath("$.data.address").value("部署配置地址"));
        mvc.perform(put("/api/admin/map-settings").header("Authorization", owner)
                        .contentType("application/json").content("{\"key\":\"legacy-service-key\"}"))
                .andExpect(status().isOk());
        mvc.perform(address).andExpect(status().isOk()).andExpect(jsonPath("$.data.address").value("旧配置地址"));
        String primary = "{\"type\":\"WEB_SERVICE\",\"label\":\"主用\",\"key\":\"limited-service-key\"}";
        mvc.perform(post("/api/admin/map-settings/credentials").header("Authorization", otherAdmin)
                        .contentType("application/json").content(primary)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/map-settings/credentials").header("Authorization", owner)
                        .contentType("application/json").content(primary)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/map-settings/credentials").header("Authorization", owner)
                        .contentType("application/json")
                        .content("{\"type\":\"WEB_SERVICE\",\"label\":\"备用\",\"key\":\"backup-service-key\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/map-settings").header("Authorization", owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentials[0].maskedKey").value("••••-key"))
                .andExpect(content().string(not(containsString("limited-service-key"))))
                .andExpect(content().string(not(containsString("backup-service-key"))));
        mvc.perform(address).andExpect(status().isOk()).andExpect(jsonPath("$.data.address").value("备用地址"));
    }

    @Test void teacherMapUsesTheSelectedJsKeyAndKeepsItsSecurityCodeOnTheServer() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(web).build();
        String owner = bearer(null, "map-owner", UserRole.ADMIN);
        String teacher = bearer(8900000000002L, "map-teacher", UserRole.TEACHER);
        var first = mvc.perform(post("/api/admin/map-settings/credentials").header("Authorization", owner)
                        .contentType("application/json").content(
                                "{\"type\":\"JS_API\",\"label\":\"地图一\",\"key\":\"first-js-key\",\"securityCode\":\"first-js-secret\"}"))
                .andExpect(status().isOk()).andReturn();
        var second = mvc.perform(post("/api/admin/map-settings/credentials").header("Authorization", owner)
                        .contentType("application/json").content(
                                "{\"type\":\"JS_API\",\"label\":\"地图二\",\"key\":\"second-js-key\",\"securityCode\":\"second-js-secret\"}"))
                .andExpect(status().isOk()).andReturn();
        var json = web.getBean(tools.jackson.databind.ObjectMapper.class);
        long firstId = json.readTree(first.getResponse().getContentAsString()).at("/data/credentials/0/id").asLong();
        long secondId = json.readTree(second.getResponse().getContentAsString()).at("/data/credentials/1/id").asLong();
        var config = mvc.perform(get("/api/location/map-config").header("Authorization", teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.key").value("first-js-key"))
                .andExpect(content().string(not(containsString("first-js-secret")))).andReturn();
        var cookie = config.getResponse().getCookie("amap_map");
        mvc.perform(get("/api/location/_AMapService/v3/ip")).andExpect(status().isForbidden());
        mvc.perform(get("/api/location/_AMapService/v3/ip?key=first-js-key").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("1"));
        mvc.perform(put("/api/admin/map-settings/credentials/JS_API/order").header("Authorization", owner)
                        .contentType("application/json").content("{\"ids\":[" + secondId + "," + firstId + "]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/location/map-config").header("Authorization", teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.key").value("second-js-key"));
    }

    private String bearer(Long userId, String subject, String role) {
        return "Bearer " + TokenUtil.createToken(new AuthUser(userId, subject, role));
    }
}

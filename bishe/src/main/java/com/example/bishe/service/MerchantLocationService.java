package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MerchantLocationService {
    public record Point(double latitude, double longitude) {}

    private static final Set<String> RETRY_WITH_BACKUP = Set.of(
            "10001", "10002", "10003", "10004", "10005", "10006", "10007", "10009", "10010",
            "10012", "10013", "10014", "10019", "10020", "10021", "10044", "40002");

    private final AdminMapSettingsService settings;
    private final JsonMapper json;
    private final String restBaseUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, String> cityCache = new LinkedHashMap<>();
    private List<String> cachedKeys = List.of();

    public MerchantLocationService(AdminMapSettingsService settings, JsonMapper json,
                                   @Value("${app.amap.rest-base-url:https://restapi.amap.com}") String restBaseUrl) {
        this.settings = settings;
        this.json = json;
        this.restBaseUrl = restBaseUrl;
    }

    public boolean available() {
        return !settings.effectiveWebKeys().isEmpty();
    }

    public Map<String, String> city(double latitude, double longitude) {
        requireCoordinates(latitude, longitude);
        List<String> keys = settings.effectiveWebKeys();
        String cell = String.format(Locale.ROOT, "%.2f,%.2f", longitude, latitude);
        synchronized (cityCache) {
            if (!keys.equals(cachedKeys)) {
                cityCache.clear();
                cachedKeys = keys;
            }
            if (cityCache.containsKey(cell)) return Map.of("city", cityCache.get(cell));
        }

        String city = withKeys(keys, key -> {
            var address = reverseGeocode(key, latitude, longitude).path("addressComponent");
            String value = address.path("city").isString() ? address.path("city").asText() : "";
            if (value.isBlank()) value = address.path("province").asText("");
            if (value.isBlank()) throw new IOException();
            return value;
        }, "当前未启用自动识别城市，请手动填写发货城市", "城市识别暂不可用，请手动填写");
        synchronized (cityCache) {
            if (keys.equals(cachedKeys)) {
                if (cityCache.size() >= 500) cityCache.clear();
                cityCache.put(cell, city);
            }
        }
        return Map.of("city", city);
    }

    public String address(double latitude, double longitude) {
        requireCoordinates(latitude, longitude);
        return withKeys(settings.effectiveWebKeys(), key -> {
            String value = reverseGeocode(key, latitude, longitude).path("formatted_address").asText("");
            if (value.isBlank()) throw new IOException();
            return value;
        }, "当前未启用地址定位，请手动填写", "地址定位暂不可用，请重试或手动填写");
    }

    public Point mapPosition(double latitude, double longitude) {
        requireCoordinates(latitude, longitude);
        return withKeys(settings.effectiveWebKeys(), key -> {
            String[] coordinates = convertedCoordinates(key, latitude, longitude).split(",");
            try {
                double lat = Double.parseDouble(coordinates[1]);
                double lon = Double.parseDouble(coordinates[0]);
                requireCoordinates(lat, lon);
                return new Point(lat, lon);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException | BusinessException e) {
                throw new IOException();
            }
        }, "尚未配置高德 Web 服务 Key", "定位服务暂不可用，请重试");
    }

    private void requireCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) throw new BusinessException("定位坐标无效");
    }

    private JsonNode reverseGeocode(String key, double latitude, double longitude) throws IOException, InterruptedException {
        String point = convertedCoordinates(key, latitude, longitude);
        return get("/v3/geocode/regeo?key=" + encode(key) + "&extensions=base&location=" + encode(point)).path("regeocode");
    }

    private String convertedCoordinates(String key, double latitude, double longitude) throws IOException, InterruptedException {
        String location = String.format(Locale.ROOT, "%.6f,%.6f", longitude, latitude);
        String result = get("/v3/assistant/coordinate/convert?key=" + encode(key)
                + "&coordsys=gps&locations=" + location).path("locations").asText("");
        if (result.isBlank()) throw new IOException();
        return result;
    }

    private JsonNode get(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(restBaseUrl + path)).timeout(Duration.ofSeconds(4)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 401 || status == 403 || status == 429) throw new KeyUnavailable();
        if (response.statusCode() != 200) throw new IOException();
        var body = json.readTree(response.body());
        if ("1".equals(body.path("status").asText())) return body;
        if (RETRY_WITH_BACKUP.contains(body.path("infocode").asText())) throw new KeyUnavailable();
        throw new IOException();
    }

    private <T> T withKeys(List<String> keys, MapOperation<T> operation, String unconfigured, String failure) {
        if (keys.isEmpty()) throw new BusinessException(503, unconfigured);
        for (String key : keys) {
            try {
                return operation.run(key);
            } catch (KeyUnavailable e) {
                // The next configured key may have its own quota or permissions.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException | tools.jackson.core.JacksonException e) {
                break;
            }
        }
        throw new BusinessException(503, failure);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface MapOperation<T> {
        T run(String key) throws IOException, InterruptedException;
    }

    private static class KeyUnavailable extends IOException {}
}

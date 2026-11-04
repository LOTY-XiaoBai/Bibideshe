package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class MerchantLocationService {
    private final String key;
    private final JsonMapper json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String,String> cache=new LinkedHashMap<>();
    public MerchantLocationService(@Value("${app.merchant.amap-key:}")String key,JsonMapper json){this.key=key;this.json=json;}
    public boolean available(){return !key.isBlank();}
    public synchronized Map<String,String> city(double lat,double lon){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat< -90||lat>90||lon< -180||lon>180)throw new BusinessException("定位坐标无效");
        if(!available())throw new BusinessException(503,"当前未启用自动识别城市，请手动填写发货城市");
        // 浏览器返回 WGS84，经坐标转换接口后再查询城市；不持久化精确位置。
        String cell=String.format(Locale.ROOT,"%.2f,%.2f",lon,lat);
        if(cache.containsKey(cell))return Map.of("city",cache.get(cell));
        try{
            String auth=URLEncoder.encode(key,StandardCharsets.UTF_8),location=String.format(Locale.ROOT,"%.6f,%.6f",lon,lat);
            var converted=get("https://restapi.amap.com/v3/assistant/coordinate/convert?key="+auth+"&coordsys=gps&locations="+location);
            String coords=converted.path("locations").asText();
            if(coords.isBlank())throw new IllegalStateException();
            var result=get("https://restapi.amap.com/v3/geocode/regeo?key="+auth+"&extensions=base&location="+URLEncoder.encode(coords,StandardCharsets.UTF_8));
            var address=result.path("regeocode").path("addressComponent");
            String city=address.path("city").isString()?address.path("city").asText():"";
            if(city.isBlank())city=address.path("province").asText();
            if(city.isBlank())throw new IllegalStateException();
            if(cache.size()>=500)cache.clear();cache.put(cell,city);return Map.of("city",city);
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new BusinessException(503,"城市识别暂不可用，请手动填写");}
        catch(Exception e){throw new BusinessException(503,"城市识别暂不可用，请手动填写");}
    }
    private tools.jackson.databind.JsonNode get(String url)throws Exception{
        var response=client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new IllegalStateException();
        var node=json.readTree(response.body());if(!"1".equals(node.path("status").asText()))throw new IllegalStateException();return node;
    }
}

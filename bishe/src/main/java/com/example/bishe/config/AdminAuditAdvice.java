package com.example.bishe.config;
import com.example.bishe.common.*;
import com.example.bishe.service.AdminOperationService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.HandlerMapping;
import java.util.*;
/** 为尚无业务留痕的管理员写操作提供统一兜底；不保存密码、Token或整个请求正文。 */
@RestControllerAdvice
public class AdminAuditAdvice implements ResponseBodyAdvice<Object> {
    private final AdminOperationService operations;
    public AdminAuditAdvice(AdminOperationService operations){this.operations=operations;}
    public boolean supports(MethodParameter type,Class<? extends HttpMessageConverter<?>> converter){return true;}
    public Object beforeBodyWrite(Object body,MethodParameter type,MediaType media,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
        var u=AuthContext.get();String path=request.getURI().getPath();
        if(u==null||!UserRole.ADMIN.equals(u.getRole())||!(body instanceof Result<?> r)||r.getCode()!=200||request.getMethod()==HttpMethod.GET||path.matches(".*(/ads?(/|$)|/orders?(/|$)|/shop(/|$)|/extra/products(/|$)).*")||path.contains("/unread"))return body;
        if(request instanceof ServletServerHttpRequest servlet){
            var req=servlet.getServletRequest();if(Boolean.TRUE.equals(req.getAttribute("adminOperationRecorded")))return body;
            Operation annotation=type.getMethodAnnotation(Operation.class);
            String action=annotation==null?"管理操作":annotation.summary();
            Map<String,Object> identifiers=new LinkedHashMap<>();
            Object vars=req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);if(vars instanceof Map<?,?> map)map.forEach((k,v)->identifiers.put(k.toString(),v));
            req.getParameterMap().forEach((k,v)->{if(k.matches("(?i).*(id|type|action)$"))identifiers.put(k,String.join(",",v));});
            operations.record(action,"MANAGEMENT",identifiers.toString(),"",null,"",request.getMethod()+" "+path+"；对象："+identifiers);
        }
        return body;
    }
}

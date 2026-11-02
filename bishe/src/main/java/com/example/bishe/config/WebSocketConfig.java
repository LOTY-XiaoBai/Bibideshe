package com.example.bishe.config;

import com.example.bishe.common.AuthUser;
import com.example.bishe.common.BusinessException;
import com.example.bishe.service.AuthSessionService;
import com.example.bishe.service.GroupService;
import com.example.bishe.util.TokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.Arrays;

/** WebSocket(STOMP) 鉴权及订阅授权。 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String GROUP_TOPIC_PREFIX = "/topic/group/";
    private final String[] allowedOrigins;
    private final AuthSessionService authSessionService;
    private final GroupService groupService;

    public WebSocketConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins,
                           AuthSessionService authSessionService,
                           GroupService groupService) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty() && !"*".equals(s)).toArray(String[]::new);
        if (this.allowedOrigins.length == 0) {
            throw new IllegalStateException("WebSocket 允许来源不能为 *");
        }
        this.authSessionService = authSessionService;
        this.groupService = groupService;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins).withSockJS();
        registry.addEndpoint("/ws-raw").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) return message;
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = firstHeader(accessor, "Authorization");
                    if (token == null || token.isBlank()) token = firstHeader(accessor, "token");
                    AuthUser user = TokenUtil.parseToken(token);
                    authSessionService.assertActive(user);
                    accessor.setUser(new StompPrincipal(user));
                    return message;
                }
                if (StompCommand.DISCONNECT.equals(accessor.getCommand())) return message;
                AuthUser user = requireAuthUser(accessor.getUser());
                authSessionService.assertActive(user);
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeSubscription(user, accessor.getDestination());
                }
                return message;
            }
        });
    }

    private void authorizeSubscription(AuthUser user, String destination) {
        if (destination == null) throw new BusinessException(403, "订阅地址不合法");
        if (destination.startsWith(GROUP_TOPIC_PREFIX)) {
            String groupPart = destination.substring(GROUP_TOPIC_PREFIX.length());
            try {
                Long groupId = Long.valueOf(groupPart);
                groupService.requireActiveGroup(groupId);
                groupService.requireMember(groupId, user.getUserId());
                return;
            } catch (NumberFormatException e) {
                throw new BusinessException(403, "群聊订阅地址不合法");
            }
        }
        if (!"/user/queue/chat".equals(destination) && !"/user/queue/chat-ack".equals(destination)) {
            throw new BusinessException(403, "无权订阅该频道");
        }
    }

    private static AuthUser requireAuthUser(Principal principal) {
        if (principal instanceof StompPrincipal stompPrincipal) return stompPrincipal.getAuthUser();
        throw new BusinessException(401, "WebSocket 未登录或登录已失效");
    }

    private static String firstHeader(StompHeaderAccessor accessor, String name) {
        if (accessor.getNativeHeader(name) == null || accessor.getNativeHeader(name).isEmpty()) return null;
        return accessor.getNativeHeader(name).get(0);
    }

    public static class StompPrincipal implements Principal {
        private final AuthUser authUser;
        public StompPrincipal(AuthUser authUser) { this.authUser = authUser; }
        @Override
        public String getName() {
            return authUser.getUserId() == null ? authUser.getSubject() : String.valueOf(authUser.getUserId());
        }
        public AuthUser getAuthUser() { return authUser; }
    }
}

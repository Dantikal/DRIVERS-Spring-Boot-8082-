package com.drivers.config.websocket;

import com.drivers.shared.util.CustomUserDetailsService;
import com.drivers.shared.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String phone = jwtUtil.extractUserName(token);
                    if (phone != null) {
                        List<String> rolesStr = jwtUtil.extractRoles(token);
                        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
                        for (String role : rolesStr) {
                            String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                            authorities.add(new SimpleGrantedAuthority(authority));
                        }

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                phone, null, authorities
                        );
                        
                        Claims claims = jwtUtil.extractAllClaims(token);
                        String driverIdStr = claims.get("driverId", String.class);
                        if (driverIdStr != null) {
                            accessor.getSessionAttributes().put("driverId", UUID.fromString(driverIdStr));
                        }
                        
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        accessor.setUser(authentication);
                        log.info("WebSocket connection authenticated for user {}", phone);
                    }
                } catch (Exception e) {
                    log.error("WebSocket JWT Authentication failed: {}", e.getMessage());
                }
            } else {
                log.warn("WebSocket connection attempt without Bearer token");
            }
        }
        return message;
    }
}

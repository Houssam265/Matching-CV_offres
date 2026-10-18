package com.gi3.matchingcv.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Configuration WebSocket + STOMP pour les notifications en temps réel.
 *
 * // TODO sécurité : à remplacer par l'authentification JWT avec Spring Security.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Active le courtier en mémoire pour les destinations /queue (privées) et /topic (publiques)
        config.enableSimpleBroker("/queue", "/topic");
        // Préfixe pour les messages envoyés depuis le client vers le serveur
        config.setApplicationDestinationPrefixes("/app");
        // Préfixe pour les messages ciblant un utilisateur spécifique : /user/queue/notifications
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Point d'entrée WebSocket STOMP avec repli SockJS
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(new UserHandshakeHandler())
                .withSockJS();

        // Point d'entrée standard (sans SockJS)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(new UserHandshakeHandler());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // TODO sécurité : à remplacer par le JWT avec Spring Security
                    String userId = accessor.getFirstNativeHeader("userId");
                    if (userId == null || userId.isBlank()) {
                        userId = accessor.getFirstNativeHeader("user-id");
                    }
                    if (userId != null && !userId.isBlank()) {
                        final String finalUserId = userId.trim();
                        accessor.setUser(() -> finalUserId);
                    }
                }
                return message;
            }
        });
    }

    /**
     * HandshakeHandler attribuant un Principal basé sur l'identifiant utilisateur passé
     * en paramètre de requête HTTP (ex: /ws?userId=42).
     */
    private static class UserHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
            // TODO sécurité : à remplacer par le JWT avec Spring Security
            if (request instanceof ServletServerHttpRequest servletRequest) {
                String userIdParam = servletRequest.getServletRequest().getParameter("userId");
                if (userIdParam != null && !userIdParam.isBlank()) {
                    final String uid = userIdParam.trim();
                    return () -> uid;
                }
            }
            String query = request.getURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && "userId".equalsIgnoreCase(pair[0])) {
                        final String uid = pair[1].trim();
                        return () -> uid;
                    }
                }
            }
            return null;
        }
    }
}

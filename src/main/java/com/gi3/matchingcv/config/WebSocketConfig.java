package com.gi3.matchingcv.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.security.access.AccessDeniedException;
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
        registry.addEndpoint("/ws");
    }
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var a=MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);
                if(a==null) return message;
                if(a.getUser()==null) throw new AccessDeniedException("Connexion requise.");
                if(a.getCommand()==StompCommand.SEND || (a.getCommand()==StompCommand.SUBSCRIBE
                        && !"/user/queue/notifications".equals(a.getDestination())))
                    throw new AccessDeniedException("Destination interdite.");
                return message;
            }
        });
    }
}

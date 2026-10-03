package sm.core.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import jakarta.annotation.PreDestroy;
import sm.core.security.WebSocketSessionGuard;

/**
 * Configuracao WebSocket/STOMP com limites defensivos:
 * origens permitidas configuraveis, limite de tamanho de mensagem, limite de
 * tempo de envio, limite de sessoes por cliente/total e pools de mensagens
 * com filas limitadas (para nao acumular trabalho em memoria).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SecurityProperties securityProperties;
    private final WebSocketSessionGuard sessionGuard;
    private final ThreadPoolTaskScheduler heartbeatScheduler;

    public WebSocketConfig(SecurityProperties securityProperties, WebSocketSessionGuard sessionGuard) {
        this.securityProperties = securityProperties;
        this.sessionGuard = sessionGuard;

        this.heartbeatScheduler = new ThreadPoolTaskScheduler();
        this.heartbeatScheduler.setPoolSize(1);
        this.heartbeatScheduler.setThreadNamePrefix("ws-heartbeat-");
        this.heartbeatScheduler.initialize();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Broker simples em memoria para enviar mensagens aos clientes.
        // O heartbeat permite detetar/libertar ligacoes mortas rapidamente.
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] { 10000, 10000 })
                .setTaskScheduler(heartbeatScheduler);
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        WebSocketLimits limits = securityProperties.getWebsocket();
        List<String> origins = limits.getAllowedOriginPatterns();
        if (origins == null || origins.isEmpty()) {
            origins = List.of("*");
        }

        // Endpoint para conexao WebSocket (com limite de origens e de sessoes).
        registry.addEndpoint("/ws-tournament")
                .setAllowedOriginPatterns(origins.toArray(new String[0]))
                .addInterceptors(sessionGuard)
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        WebSocketLimits limits = securityProperties.getWebsocket();
        registration.setMessageSizeLimit(limits.getMessageSizeLimitBytes());
        registration.setSendBufferSizeLimit(limits.getSendBufferSizeLimitBytes());
        registration.setSendTimeLimit(limits.getSendTimeLimitMillis());
        registration.setTimeToFirstMessage(limits.getTimeToFirstMessageMillis());
        registration.addDecoratorFactory(sessionGuard);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        WebSocketLimits limits = securityProperties.getWebsocket();
        registration.taskExecutor()
                .corePoolSize(limits.getInboundCorePoolSize())
                .maxPoolSize(limits.getInboundMaxPoolSize())
                .queueCapacity(limits.getInboundQueueCapacity())
                .keepAliveSeconds(60);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        WebSocketLimits limits = securityProperties.getWebsocket();
        registration.taskExecutor()
                .corePoolSize(limits.getOutboundCorePoolSize())
                .maxPoolSize(limits.getOutboundMaxPoolSize())
                .queueCapacity(limits.getOutboundQueueCapacity())
                .keepAliveSeconds(60);
    }

    @PreDestroy
    public void shutdownHeartbeatScheduler() {
        heartbeatScheduler.shutdown();
    }
}

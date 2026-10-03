package sm.core.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Limites aplicados as ligacoes e mensagens WebSocket/STOMP.
 *
 * <p>Propriedades: {@code sm.core.security.websocket.*}</p>
 */
public class WebSocketLimits {

    private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));
    private int maxSessionsPerClient = 3;
    private int maxTotalSessions = 200;
    private int messageSizeLimitBytes = 65536;
    private int sendBufferSizeLimitBytes = 262144;
    private int sendTimeLimitMillis = 10000;
    private int timeToFirstMessageMillis = 20000;
    private int inboundCorePoolSize = 2;
    private int inboundMaxPoolSize = 8;
    private int inboundQueueCapacity = 256;
    private int outboundCorePoolSize = 2;
    private int outboundMaxPoolSize = 8;
    private int outboundQueueCapacity = 512;

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    public int getMaxSessionsPerClient() {
        return maxSessionsPerClient;
    }

    public void setMaxSessionsPerClient(int maxSessionsPerClient) {
        this.maxSessionsPerClient = maxSessionsPerClient;
    }

    public int getMaxTotalSessions() {
        return maxTotalSessions;
    }

    public void setMaxTotalSessions(int maxTotalSessions) {
        this.maxTotalSessions = maxTotalSessions;
    }

    public int getMessageSizeLimitBytes() {
        return messageSizeLimitBytes;
    }

    public void setMessageSizeLimitBytes(int messageSizeLimitBytes) {
        this.messageSizeLimitBytes = messageSizeLimitBytes;
    }

    public int getSendBufferSizeLimitBytes() {
        return sendBufferSizeLimitBytes;
    }

    public void setSendBufferSizeLimitBytes(int sendBufferSizeLimitBytes) {
        this.sendBufferSizeLimitBytes = sendBufferSizeLimitBytes;
    }

    public int getSendTimeLimitMillis() {
        return sendTimeLimitMillis;
    }

    public void setSendTimeLimitMillis(int sendTimeLimitMillis) {
        this.sendTimeLimitMillis = sendTimeLimitMillis;
    }

    public int getTimeToFirstMessageMillis() {
        return timeToFirstMessageMillis;
    }

    public void setTimeToFirstMessageMillis(int timeToFirstMessageMillis) {
        this.timeToFirstMessageMillis = timeToFirstMessageMillis;
    }

    public int getInboundCorePoolSize() {
        return inboundCorePoolSize;
    }

    public void setInboundCorePoolSize(int inboundCorePoolSize) {
        this.inboundCorePoolSize = inboundCorePoolSize;
    }

    public int getInboundMaxPoolSize() {
        return inboundMaxPoolSize;
    }

    public void setInboundMaxPoolSize(int inboundMaxPoolSize) {
        this.inboundMaxPoolSize = inboundMaxPoolSize;
    }

    public int getInboundQueueCapacity() {
        return inboundQueueCapacity;
    }

    public void setInboundQueueCapacity(int inboundQueueCapacity) {
        this.inboundQueueCapacity = inboundQueueCapacity;
    }

    public int getOutboundCorePoolSize() {
        return outboundCorePoolSize;
    }

    public void setOutboundCorePoolSize(int outboundCorePoolSize) {
        this.outboundCorePoolSize = outboundCorePoolSize;
    }

    public int getOutboundMaxPoolSize() {
        return outboundMaxPoolSize;
    }

    public void setOutboundMaxPoolSize(int outboundMaxPoolSize) {
        this.outboundMaxPoolSize = outboundMaxPoolSize;
    }

    public int getOutboundQueueCapacity() {
        return outboundQueueCapacity;
    }

    public void setOutboundQueueCapacity(int outboundQueueCapacity) {
        this.outboundQueueCapacity = outboundQueueCapacity;
    }
}

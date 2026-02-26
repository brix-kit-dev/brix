package io.brix.platform.gateway.config.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关分布式链路追踪配置属性类
 * 
 * <p>提供 Micrometer Tracing + OpenTelemetry + Jaeger 的配置参数，包括</p>
 * <ul>
 *   <li>是否启用链路追踪</li>
 *   <li>OTLP/Jaeger 服务端点</li>
 *   <li>采样率配</li>
 *   <li>服务名称</li>
 *   <li>日志 MDC 注入配置</li>
 * </ul>
 * 
 * <p>P106 任务产出物（OpenTelemetry 升级版）</p>
 * 
 * @author Brix Platform Authors Platform
 * @version 2.0.0
 * @since 2025-12-17
 */
@Component
@ConfigurationProperties(prefix = "gateway.tracing")
public class TracingProperties {
    
    /**
     * 是否启用链路追踪
     * <p>生产环境建议启用，开发环境可按需关闭</p>
     */
    private boolean enabled = true;
    
    /**
     * 服务名称
     * <p>用于在 Jaeger UI 中标识当前服</p>
     */
    private String serviceName = "platform-gateway";
    
    /**
     * 采样率（0.0 ~ 1.0
     * <p>1.0 表示 100% 采样.1 表示 10% 采样</p>
     * <p>生产环境高流量场景建议设0.1 以降低性能开销</p>
     */
    private float samplingProbability = 1.0f;
    
    /**
     * 是否traceId 注入日志 MDC
     * <p>启用后可在日志中通过 %X{traceId} 输出追踪 ID</p>
     */
    private boolean logMdcEnabled = true;
    
    /**
     * OTLP 配置（用Jaeger
     */
    private OtlpConfig otlp = new OtlpConfig();
    
    /**
     * 传播方式配置
     */
    private PropagationConfig propagation = new PropagationConfig();
    
    /**
     * 不追踪的路径列表（用于排除健康检查等高频低价值请求）
     */
    private List<String> excludedPaths = new ArrayList<>();
    
    // ========== Getters & Setters ==========
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public float getSamplingProbability() {
        return samplingProbability;
    }
    
    public void setSamplingProbability(float samplingProbability) {
        this.samplingProbability = samplingProbability;
    }
    
    public boolean isLogMdcEnabled() {
        return logMdcEnabled;
    }
    
    public void setLogMdcEnabled(boolean logMdcEnabled) {
        this.logMdcEnabled = logMdcEnabled;
    }
    
    public OtlpConfig getOtlp() {
        return otlp;
    }
    
    public void setOtlp(OtlpConfig otlp) {
        this.otlp = otlp;
    }
    
    public PropagationConfig getPropagation() {
        return propagation;
    }
    
    public void setPropagation(PropagationConfig propagation) {
        this.propagation = propagation;
    }
    
    public List<String> getExcludedPaths() {
        return excludedPaths;
    }
    
    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }
    
    /**
     * OTLP 配置内部
     * 
     * <p>OpenTelemetry Protocol (OTLP) OpenTelemetry 的标准数据导出协</p>
     * <p>Jaeger 1.35 版本开始原生支OTLP gRPC 协议</p>
     */
    public static class OtlpConfig {
        
        /**
         * OTLP gRPC 绔偣鍦板潃
         * <p>Jaeger Collector 默认 OTLP gRPC 端口4317</p>
         */
        private String endpoint = "http://localhost:4317";
        
        /**
         * 导出超时时间（毫秒）
         */
        private int timeout = 10000;
        
        /**
         * 压缩方式（none, gzip
         */
        private String compression = "none";
        
        public String getEndpoint() {
            return endpoint;
        }
        
        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
        
        public int getTimeout() {
            return timeout;
        }
        
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
        
        public String getCompression() {
            return compression;
        }
        
        public void setCompression(String compression) {
            this.compression = compression;
        }
    }
    
    /**
     * 传播方式配置内部
     */
    public static class PropagationConfig {
        
        /**
         * 传播类型
         * <ul>
         *   <li>W3C: W3C Trace Context 标准（推荐）</li>
         *   <li>B3: Zipkin B3 格式（兼容旧系统</li>
         *   <li>B3_MULTI: B3 多头格式</li>
         * </ul>
         */
        private String type = "W3C";
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
    }
}

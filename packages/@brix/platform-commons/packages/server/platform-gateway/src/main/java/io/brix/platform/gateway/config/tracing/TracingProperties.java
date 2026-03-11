/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.platform.gateway.config.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * GatewaydistributedDistributed Tracing Configuration Propertiesclass
 * 
 * <p>provide Micrometer Tracing + OpenTelemetry + Jaeger ofconfigurationparameter，packagebracket</p>
 * <ul>
 *   <li>Whether to enable distributed tracing</li>
 *   <li>OTLP/Jaeger serviceendpoint</li>
 *   <li>Sampling rateconfiguration</li>
 *   <li>Service name</li>
 *   <li>log MDC injectconfiguration</li>
 * </ul>
 * 
 * <p>P106 taskproduceoutobject（OpenTelemetry upgradelevelversion）</p>
 * 
 * @author Brix Platform Authors Platform
 * @version 2.0.0
 * @since 2025-12-17
 */
@Component
@ConfigurationProperties(prefix = "gateway.tracing")
public class TracingProperties {
    
    /**
     * Whether to enable distributed tracing
     * <p>productionenvironmentrecommendedenable，opensendenvironmentcanbyneedclosed</p>
     */
    private boolean enabled = true;
    
    /**
     * Service name
     * <p>used foron Jaeger UI inidentifierwhenbeforeservice</p>
     */
    private String serviceName = "platform-gateway";
    
    /**
     * Sampling rate（0.0 ~ 1.0
     * <p>1.0 represents 100% sampling.1 represents 10% sampling</p>
     * <p>productionenvironmenthighflowamountscenariorecommendedset0.1 toreduceitycanopendestroy</p>
     */
    private float samplingProbability = 1.0f;
    
    /**
     * whethertraceId injectlog MDC
     * <p>enableaftercanonloginvia %X{traceId} outputtrace ID</p>
     */
    private boolean logMdcEnabled = true;
    
    /**
     * OTLP configuration（useJaeger
     */
    private OtlpConfig otlp = new OtlpConfig();
    
    /**
     * propagationwayconfiguration
     */
    private PropagationConfig propagation = new PropagationConfig();
    
    /**
     * nottraceofpathlist（used forexcludehealthchecketchighlow frequencyvaluerequest）
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
     * OTLP configurationinternal
     * 
     * <p>OpenTelemetry Protocol (OTLP) OpenTelemetry ofstandardcountdataguideoutcooperate</p>
     * <p>Jaeger 1.35 versionstartnativesupportOTLP gRPC protocol</p>
     */
    public static class OtlpConfig {
        
        /**
         * OTLP gRPC 
         * <p>Jaeger Collector default OTLP gRPC endport4317</p>
         */
        private String endpoint = "http://localhost:4317";
        
        /**
         * guideouttimeouttime（ms）
         */
        private int timeout = 10000;
        
        /**
         * compressway（none, gzip
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
     * propagationwayconfigurationinternal
     */
    public static class PropagationConfig {
        
        /**
         * propagationtype
         * <ul>
         *   <li>W3C: W3C Trace Context standard（recommended）</li>
         *   <li>B3: Zipkin B3 format（compatibleallowoldsystem</li>
         *   <li>B3_MULTI: B3 multipleheaderformat</li>
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

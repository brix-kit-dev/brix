package io.brix.platform.common.tenant;

/**
 * 租户附加信息
 * 
 * <p>可选的租户元数据，用于存储租户的额外配置信息
 * 
 * @author Brix Platform Authors Platform Team
 * @since 1.0.0
 */
public class TenantInfo {
    
    /** 绉熸埛 ID */
    private final String tenantId;
    
    /** 租户名称 */
    private final String tenantName;
    
    /** 租户状*/
    private final String status;
    
    /** 租户配置（JSON 格式*/
    private final String config;

    public TenantInfo(String tenantId, String tenantName, String status) {
        this(tenantId, tenantName, status, null);
    }

    public TenantInfo(String tenantId, String tenantName, String status, String config) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.status = status;
        this.config = config;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public String getStatus() {
        return status;
    }

    public String getConfig() {
        return config;
    }

    /**
     * 创建一Builder
     * 
     * @param tenantId 绉熸埛 ID
     * @return Builder 实例
     */
    public static Builder builder(String tenantId) {
        return new Builder(tenantId);
    }

    /**
     * TenantInfo 构建
     */
    public static class Builder {
        private final String tenantId;
        private String tenantName;
        private String status = "ACTIVE";
        private String config;

        private Builder(String tenantId) {
            this.tenantId = tenantId;
        }

        public Builder tenantName(String tenantName) {
            this.tenantName = tenantName;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder config(String config) {
            this.config = config;
            return this;
        }

        public TenantInfo build() {
            return new TenantInfo(tenantId, tenantName, status, config);
        }
    }
}

package io.brix.platform.auth.oauth2;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 身份提供商（IdP）配置属性类
 * <p>
 * 支持配置多个第三方身份提供商，包括：
 * <ul>
 *   <li>Google OAuth2</li>
 *   <li>微信开放平</li>
 *   <li>GitHub（可选）</li>
 * </ul>
 * </p>
 *
 * <p>
 * 配置示例（application.yml）：
 * <pre>
 * platform:
 *   oauth2:
 *     enabled: true
 *     providers:
 *       google:
 *         enabled: true
 *         client-id: your-google-client-id
 *         client-secret: your-google-client-secret
 *         redirect-uri: http://localhost:8080/api/v1/oauth2/callback/google
 *         scope: openid,profile,email
 *       wechat:
 *         enabled: true
 *         client-id: your-wechat-app-id
 *         client-secret: your-wechat-app-secret
 *         redirect-uri: http://localhost:8080/api/v1/oauth2/callback/wechat
 *         scope: snsapi_login
 * </pre>
 * </p>
 *
 * <p>
 * 注意：此类已通过 {@code @EnableConfigurationProperties} 
 * {@link OAuth2Config} 中启用，无需添加 {@code @Component} 注解
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
@Data
@ConfigurationProperties(prefix = "platform.oauth2")
public class OAuth2Properties {

    /**
     * 是否启用 OAuth2 登录
     */
    private boolean enabled = false;

    /**
     * OAuth2 回调后的前端跳转地址
     * 默认/login/callback
     */
    private String frontendCallbackUrl = "/login/callback";

    /**
     * 登录成功后默认跳转地址
     */
    private String defaultSuccessUrl = "/";

    /**
     * 登录失败后跳转地址
     */
    private String failureUrl = "/login?error=oauth2";

    /**
     * 自动注册新用
     * OAuth2 用户首次登录且无绑定账号时，是否自动创建新用
     */
    private boolean autoRegister = true;

    /**
     * 状态参数有效期（秒
     * 用于防止 CSRF 攻击
     */
    private int stateExpireSeconds = 300;

    /**
     * 身份提供商配置映
     * Key 为提供商标识（如 google、wechat
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /**
     * 单个身份提供商配
     */
    @Data
    public static class ProviderConfig {
        /**
         * 是否启用该提供商
         */
        private boolean enabled = false;

        /**
         * 提供商显示名称（用于前端展示
         */
        private String displayName;

        /**
         * 提供商图URL CSS 类名
         */
        private String icon;

        /**
         * 客户ID（App ID
         */
        private String clientId;

        /**
         * 客户端密钥（App Secret
         */
        private String clientSecret;

        /**
         * 鎺堟潈绔偣 URL
         */
        private String authorizationUri;

        /**
         * Token 绔偣 URL
         */
        private String tokenUri;

        /**
         * 用户信息端点 URL
         */
        private String userInfoUri;

        /**
         * 回调 URI（重定向 URI
         */
        private String redirectUri;

        /**
         * 请求的权限范围（逗号分隔
         */
        private String scope;

        /**
         * 用户 ID 字段名（从用户信息响应中提取
         */
        private String userIdAttribute = "id";

        /**
         * 用户名字段名
         */
        private String userNameAttribute = "name";

        /**
         * 邮箱字段
         */
        private String emailAttribute = "email";

        /**
         * 头像字段
         */
        private String avatarAttribute = "avatar";

        /**
         * 是否需PKCE（Proof Key for Code Exchange
         */
        private boolean usePkce = false;

        /**
         * 额外请求参数
         */
        private Map<String, String> additionalParams = new HashMap<>();
    }

    /**
     * 获取启用的提供商配置
     *
     * @return 启用的提供商映射
     */
    public Map<String, ProviderConfig> getEnabledProviders() {
        Map<String, ProviderConfig> enabledProviders = new HashMap<>();
        for (Map.Entry<String, ProviderConfig> entry : providers.entrySet()) {
            if (entry.getValue().isEnabled()) {
                enabledProviders.put(entry.getKey(), entry.getValue());
            }
        }
        return enabledProviders;
    }

    /**
     * 检查指定提供商是否启用
     *
     * @param providerId 提供商标
     * @return 是否启用
     */
    public boolean isProviderEnabled(String providerId) {
        ProviderConfig config = providers.get(providerId);
        return config != null && config.isEnabled();
    }
}

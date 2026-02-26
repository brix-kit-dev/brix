package io.brix.platform.auth.oauth2;

/**
 * OAuth2 认证异常
 * <p>
 * 用于 OAuth2 登录流程中的各种异常情况
 * <ul>
 *   <li>无效state 参数</li>
 *   <li>Token 交换失败</li>
 *   <li>用户信息获取失败</li>
 *   <li>用户绑定失败</li>
 * </ul>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
public class OAuth2Exception extends RuntimeException {

    /**
     * 错误
     */
    private final String errorCode;

    /**
     * 创建 OAuth2 异常
     *
     * @param message 错误消息
     */
    public OAuth2Exception(String message) {
        super(message);
        this.errorCode = "OAUTH2_ERROR";
    }

    /**
     * 创建 OAuth2 异常
     *
     * @param message   错误消息
     * @param errorCode 错误
     */
    public OAuth2Exception(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建 OAuth2 异常
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public OAuth2Exception(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "OAUTH2_ERROR";
    }

    /**
     * 获取错误
     *
     * @return 错误
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 无效state 参数
     */
    public static OAuth2Exception invalidState() {
        return new OAuth2Exception("无效state 参数，可能存CSRF 攻击", "INVALID_STATE");
    }

    /**
     * Token 交换失败
     */
    public static OAuth2Exception tokenExchangeFailed(String reason) {
        return new OAuth2Exception("Token 交换失败: " + reason, "TOKEN_EXCHANGE_FAILED");
    }

    /**
     * 用户信息获取失败
     */
    public static OAuth2Exception userInfoFailed(String reason) {
        return new OAuth2Exception("获取用户信息失败: " + reason, "USER_INFO_FAILED");
    }

    /**
     * 提供商未启用
     */
    public static OAuth2Exception providerNotEnabled(String provider) {
        return new OAuth2Exception("不支持的登录方式: " + provider, "PROVIDER_NOT_ENABLED");
    }

    /**
     * 用户绑定失败
     */
    public static OAuth2Exception bindingFailed(String reason) {
        return new OAuth2Exception("用户绑定失败: " + reason, "BINDING_FAILED");
    }
}

package io.brix.platform.auth.oauth2;

import lombok.Data;

/**
 * OAuth2 用户信息
 * <p>
 * 从第三方身份提供商获取的标准化用户信息
 * 不同提供商的原始字段会被映射到统一的字段结构
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
@Data
public class OAuth2UserInfo {

    /**
     * 身份提供商标识（google、wechat、github
     */
    private String provider;

    /**
     * 用户在该提供商的唯一标识
     * <ul>
     *   <li>Google: sub</li>
     *   <li>寰俊: openid</li>
     *   <li>GitHub: id</li>
     * </ul>
     */
    private String providerId;

    /**
     * 用户显示名称
     */
    private String name;

    /**
     * 用户邮箱（可能为空）
     */
    private String email;

    /**
     * 用户头像 URL（可能为空）
     */
    private String avatar;

    /**
     * 原始属JSON 字符
     * 保留提供商返回的完整用户信息，便于扩
     */
    private String rawAttributes;

    /**
     * 生成唯一OAuth2 绑定标识
     * 格式: provider:providerId
     *
     * @return 绑定标识
     */
    public String getBindingKey() {
        return provider + ":" + providerId;
    }
}

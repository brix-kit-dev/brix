package io.brix.platform.auth.jwt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.exception.JwtConfigurationException;

/**
 * JWT 验证
 * <p>
 * 使用 RS256 公钥验证 Token，不负责签发
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public class JwtValidator {

    private static final Logger logger = LoggerFactory.getLogger(JwtValidator.class);

    private final JwtProperties properties;
    private final PublicKey publicKey;

    public JwtValidator(JwtProperties properties) {
        this.properties = properties;
        this.publicKey = loadPublicKey(properties.getPublicKeyPath());
    }

    /**
     * 验证 Token 并解析用户信
     *
     * @param token JWT Token
     * @return 认证用户信息
     * @throws JwtValidationException 验证失败时抛
     */
    public AuthenticatedUser validate(String token) throws JwtValidationException {
        if (token == null || token.isBlank()) {
            throw new JwtValidationException("Token is empty", JwtValidationException.Reason.EMPTY);
        }

        // 移除 Bearer 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(properties.getIssuer())
                    // P1 修复：添Audience 验证
                    .requireAudience(properties.getAudience())
                    .clockSkewSeconds(properties.getClockSkewSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return extractUser(claims);

        } catch (ExpiredJwtException e) {
            logger.debug("Token expired: {}", e.getMessage());
            throw new JwtValidationException("Token has expired", JwtValidationException.Reason.EXPIRED);
        } catch (SignatureException e) {
            logger.warn("Invalid token signature: {}", e.getMessage());
            throw new JwtValidationException("Invalid token signature", JwtValidationException.Reason.INVALID_SIGNATURE);
        } catch (MalformedJwtException e) {
            logger.warn("Malformed token: {}", e.getMessage());
            throw new JwtValidationException("Malformed token", JwtValidationException.Reason.MALFORMED);
        } catch (UnsupportedJwtException e) {
            logger.warn("Unsupported token: {}", e.getMessage());
            throw new JwtValidationException("Unsupported token", JwtValidationException.Reason.UNSUPPORTED);
        } catch (JwtException e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            throw new JwtValidationException("Token validation failed: " + e.getMessage(), 
                    JwtValidationException.Reason.INVALID);
        }
    }

    /**
     * Claims 提取用户信息
     */
    @SuppressWarnings("unchecked")
    private AuthenticatedUser extractUser(Claims claims) {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setUserId(claims.getSubject());
        user.setTenantId(claims.get("tenant_id", String.class));
        user.setUsername(claims.get("username", String.class));
        user.setEmail(claims.get("email", String.class));
        user.setTokenVersion(claims.get("tv", Long.class));
        
        // 提取角色和权
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List) {
            user.setRoles((List<String>) rolesObj);
        }
        
        Object permissionsObj = claims.get("permissions");
        if (permissionsObj instanceof List) {
            user.setPermissions((List<String>) permissionsObj);
        }
        
        return user;
    }

    /**
     * 加载 RSA 公钥
     */
    private PublicKey loadPublicKey(String path) {
        try {
            ResourceLoader resourceLoader = new DefaultResourceLoader();
            Resource resource = resourceLoader.getResource(path);
            
            try (InputStream is = resource.getInputStream()) {
                String keyContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                // 移除 PEM 头尾和换
                keyContent = keyContent
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
                
                byte[] keyBytes = Base64.getDecoder().decode(keyContent);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                
                return keyFactory.generatePublic(spec);
            }
        } catch (IOException e) {
            // [R10 Fix] Replace RuntimeException with domain-specific JwtConfigurationException
            // Public key loading failure must prevent application startup in insecure mode
            throw new JwtConfigurationException(
                "Failed to load JWT public key, authentication cannot be initialized", path, e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new JwtConfigurationException(
                "Failed to parse public key - invalid key format or unsupported algorithm", path, e);
        }
    }

    /**
     * JWT 验证异常
     */
    public static class JwtValidationException extends Exception {
        
        public enum Reason {
            EMPTY, EXPIRED, INVALID_SIGNATURE, MALFORMED, UNSUPPORTED, INVALID, REVOKED
        }
        
        private final Reason reason;

        public JwtValidationException(String message, Reason reason) {
            super(message);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}

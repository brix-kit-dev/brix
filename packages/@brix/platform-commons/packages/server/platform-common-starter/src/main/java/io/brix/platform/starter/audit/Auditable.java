package io.brix.platform.starter.audit;

import java.lang.annotation.*;

/**
 * 审计注解
 * 
 * <p>v2.1 阶段4 审计日志增强</p>
 * 
 * <p>功能说明</p>
 * <p>标记需要进行详细审计记录的方法，支持自定义审计配置</p>
 * 
 * <p>使用示例</p>
 * <pre>{@code
 * @Auditable(
 *     action = "FILE_DOWNLOAD",
 *     resource = "FileCenter",
 *     recordParams = true,
 *     recordResult = false
 * )
 * public InputStream downloadFile(Long fileId) {
 *     // ...
 * }
 * }</pre>
 * 
 * <p>审计日志输出格式</p>
 * <pre>
 * [AUDIT] action=FILE_DOWNLOAD, resource=FileCenter, userId=xxx, 
 *         params={fileId=123}, ip=127.0.0.1, status=SUCCESS, duration=50ms
 * </pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    
    /**
     * 操作类型
     * 
     * <p>描述方法执行的操作，如：CREATE、READ、UPDATE、DELETE
     * FILE_UPLOAD、FILE_DOWNLOAD、LOGIN 等</p>
     * 
     * @return 操作类型
     */
    String action();
    
    /**
     * 资源类型
     * 
     * <p>描述操作的资源对象，如：User、File、Case、Contract 等</p>
     * 
     * @return 资源类型
     */
    String resource();
    
    /**
     * 是否记录请求参数
     * 
     * <p>默认 true。注意：敏感信息请设置为 false 或使用 @SensitiveParam 注解</p>
     * 
     * @return 是否记录参数
     */
    boolean recordParams() default true;
    
    /**
     * 是否记录返回结果
     * 
     * <p>默认 false。对于大对象或流式返回，建议保持 false</p>
     * 
     * @return 是否记录结果
     */
    boolean recordResult() default false;
    
    /**
     * 敏感参数名列
     * 
     * <p>这些参数将被脱敏（显示为 ****）</p>
     * 
     * @return 敏感参数名数
     */
    String[] sensitiveParams() default {"password", "token", "secret"};
    
    /**
     * 操作描述模板
     * 
     * <p>支持 SpEL 表达式，如："用户 #{#userId} 下载了文#{#fileId}"</p>
     * 
     * @return 描述模板
     */
    String description() default "";
}

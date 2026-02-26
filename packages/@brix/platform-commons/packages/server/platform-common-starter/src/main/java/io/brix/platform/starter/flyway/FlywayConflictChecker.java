package io.brix.platform.starter.flyway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import io.brix.platform.starter.config.FlywayExtProperties;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flyway 版本冲突检测器
 * 
 * <p>在应用启动时检Flyway 迁移脚本是否存在版本冲突</p>
 * 
 * <p>检测规则：</p>
 * <ul>
 *   <li>版本号不能重</li>
 *   <li>版本号必须符合规范格</li>
 *   <li>检测到冲突时记录警告日</li>
 * </ul>
 * 
 * <p>规范的版本格式：</p>
 * <pre>
 * V{插件前缀}_{版本号}__{描述}.sql
 * 
 * 正确示例
 * - V001_001__init_schema.sql
 * - V001_002__add_column.sql
 * 
 * 错误示例
 * - V1__init_schema.sql  （未使用插件前缀
 * - V001_001_init.sql    （描述前只有一个下划线
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see FlywayExtProperties
 */
public class FlywayConflictChecker {
    
    private static final Logger log = LoggerFactory.getLogger(FlywayConflictChecker.class);
    
    /**
     * 规范的版本模式：V{插件前缀}_{版本号}__{描述}.sql
     * 
     * <p>示例：V001_001__init_schema.sql</p>
     */
    private static final Pattern VERSIONED_PATTERN = Pattern.compile(
        "V(\\d+)_(\\d+)__(.+)\\.sql", Pattern.CASE_INSENSITIVE);
    
    /**
     * 简单的版本模式：V{版本号}__{描述}.sql
     * 
     * <p>示例：V1__init_schema.sql</p>
     */
    private static final Pattern SIMPLE_PATTERN = Pattern.compile(
        "V(\\d+)__(.+)\\.sql", Pattern.CASE_INSENSITIVE);
    
    /**
     * Flyway 扩展配置
     */
    private final FlywayExtProperties flywayProperties;
    
    /**
     * 资源解析
     */
    private final PathMatchingResourcePatternResolver resolver;
    
    /**
     * 构造函数
     * 
     * @param flywayProperties Flyway 扩展配置
     */
    public FlywayConflictChecker(FlywayExtProperties flywayProperties) {
        this.flywayProperties = flywayProperties;
        this.resolver = new PathMatchingResourcePatternResolver();
    }
    
    /**
     * 执行冲突检
     * 
     * @return 检测结
     */
    public ConflictCheckResult check() {
        if (!flywayProperties.isConflictCheckEnabled()) {
            log.debug("[FlywayConflictChecker] 冲突检测已禁用");
            return ConflictCheckResult.disabled();
        }
        
        log.info("[FlywayConflictChecker] 开始检Flyway 版本冲突...");
        
        try {
            List<String> migrations = scanMigrations();
            return analyzeConflicts(migrations);
        } catch (IOException e) {
            log.error("[FlywayConflictChecker] 扫描迁移脚本失败: {}", e.getMessage());
            return ConflictCheckResult.error(e.getMessage());
        }
    }
    
    /**
     * 扫描迁移脚本
     * 
     * @return 迁移脚本文件名列
     */
    private List<String> scanMigrations() throws IOException {
        String location = flywayProperties.getLocations();
        if (location == null || location.isEmpty()) {
            location = "classpath:db/migration";
        }
        
        // 转换为资源模
        String pattern = location.replace("classpath:", "classpath:") + "/*.sql";
        
        Resource[] resources = resolver.getResources(pattern);
        List<String> migrations = new ArrayList<>();
        
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename != null && filename.startsWith("V")) {
                migrations.add(filename);
            }
        }
        
        log.debug("[FlywayConflictChecker] 扫描{} 个迁移脚", migrations.size());
        return migrations;
    }
    
    /**
     * 分析版本冲突
     * 
     * @param migrations 迁移脚本列表
     * @return 检测结
     */
    private ConflictCheckResult analyzeConflicts(List<String> migrations) {
        Map<String, List<String>> versionToFiles = new HashMap<>();
        List<String> nonCompliantFiles = new ArrayList<>();
        
        for (String migration : migrations) {
            Matcher versionedMatcher = VERSIONED_PATTERN.matcher(migration);
            Matcher simpleMatcher = SIMPLE_PATTERN.matcher(migration);
            
            if (versionedMatcher.matches()) {
                // 规范格式：提取完整版本号
                String version = versionedMatcher.group(1) + "_" + versionedMatcher.group(2);
                versionToFiles.computeIfAbsent(version, k -> new ArrayList<>()).add(migration);
            } else if (simpleMatcher.matches()) {
                // 简单格式：记录为不合规
                String version = simpleMatcher.group(1);
                versionToFiles.computeIfAbsent(version, k -> new ArrayList<>()).add(migration);
                nonCompliantFiles.add(migration);
            }
        }
        
        // 检查冲
        List<String> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : versionToFiles.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.add(String.format("鐗堟湰 %s: %s", 
                    entry.getKey(), String.join(", ", entry.getValue())));
            }
        }
        
        // 输出警告
        if (!conflicts.isEmpty()) {
            log.warn("[FlywayConflictChecker] 检测到版本冲突");
            for (String conflict : conflicts) {
                log.warn("[FlywayConflictChecker]   - {}", conflict);
            }
        }
        
        if (!nonCompliantFiles.isEmpty()) {
            log.warn("[FlywayConflictChecker] 以下文件未使用规范的版本格式（应使用 V{插件前缀}_{版本号}__{描述}.sql）：");
            for (String file : nonCompliantFiles) {
                log.warn("[FlywayConflictChecker]   - {}", file);
            }
        }
        
        if (conflicts.isEmpty() && nonCompliantFiles.isEmpty()) {
            log.info("[FlywayConflictChecker] 未检测到版本冲突，所有脚本符合规");
        }
        
        return new ConflictCheckResult(
            conflicts.isEmpty() && nonCompliantFiles.isEmpty(),
            conflicts,
            nonCompliantFiles,
            null
        );
    }
    
    /**
     * 冲突检测结
     */
    public record ConflictCheckResult(
        boolean passed,
        List<String> conflicts,
        List<String> nonCompliantFiles,
        String error
    ) {
        public static ConflictCheckResult disabled() {
            return new ConflictCheckResult(true, List.of(), List.of(), null);
        }
        
        public static ConflictCheckResult error(String error) {
            return new ConflictCheckResult(false, List.of(), List.of(), error);
        }
        
        public boolean hasConflicts() {
            return conflicts != null && !conflicts.isEmpty();
        }
        
        public boolean hasNonCompliantFiles() {
            return nonCompliantFiles != null && !nonCompliantFiles.isEmpty();
        }
    }
}

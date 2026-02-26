package io.brix.platform.starter.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 插件 Manifest 扫描
 * 
 * <p>扫描 classpath 下所JAR 包中META-INF/plugin-manifest.json 文件
 * 聚合所有插件的 UI 契约和事件契约</p>
 * 
 * <p>这是方案B（插件自描述）的核心实现</p>
 * <ul>
 *   <li>插件自治：每个插件在自己JAR 中定manifest</li>
 *   <li>服务聚合：服务启动时自动扫描所有依赖插件的 manifest</li>
 *   <li>动态组合：服务更换插件组合时，菜单自动跟着</li>
 * </ul>
 * 
 * <p>扫描路径</p>
 * <pre>
 * classpath*:META-INF/plugin-manifest.json
 * </pre>
 * 
 * <p>使用示例</p>
 * <pre>
 * {@code
 * @Autowired
 * private PluginManifestScanner scanner;
 * 
 * // 获取所有插manifest
 * List<PluginManifest> manifests = scanner.scanManifests();
 * 
 * // 聚合 UI 契约
 * Map<String, Object> aggregatedUi = scanner.aggregateUiContracts();
 * }
 * </pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Component
public class PluginManifestScanner {
    
    private static final Logger log = LoggerFactory.getLogger(PluginManifestScanner.class);
    
    /** Manifest 文件路径模式 */
    private static final String MANIFEST_PATTERN = "classpath*:META-INF/plugin-manifest.json";
    
    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resolver;
    
    /** 缓存扫描结果 */
    private List<PluginManifest> cachedManifests;
    
    public PluginManifestScanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.resolver = new PathMatchingResourcePatternResolver();
    }
    
    /**
     * 扫描所有插manifest
     * 
     * <p>扫描 classpath 下所有 META-INF/plugin-manifest.json 文件并解析</p>
     * 
     * @return 插件 manifest 列表
     */
    public List<PluginManifest> scanManifests() {
        if (cachedManifests != null) {
            return cachedManifests;
        }
        
        List<PluginManifest> manifests = new ArrayList<>();
        
        try {
            Resource[] resources = resolver.getResources(MANIFEST_PATTERN);
            log.info("[PluginManifestScanner] 发现 {} plugin-manifest.json 文件", resources.length);
            
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    PluginManifest manifest = objectMapper.readValue(is, PluginManifest.class);
                    if (manifest.getName() != null) {
                        manifests.add(manifest);
                        log.info("[PluginManifestScanner] 加载插件 manifest: {} v{}", 
                            manifest.getName(), manifest.getVersion());
                    }
                } catch (IOException e) {
                    log.warn("[PluginManifestScanner] 解析 manifest 失败: {}, 错误: {}", 
                        resource.getDescription(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("[PluginManifestScanner] 扫描 manifest 文件失败: {}", e.getMessage());
        }
        
        log.info("[PluginManifestScanner] 共加{} 个插manifest", manifests.size());
        cachedManifests = manifests;
        return manifests;
    }
    
    /**
     * 聚合所有插件的 UI 契约
     * 
     * <p>将所有插件的 UI 配置合并为 Plugin Engine 期望的格式</p>
     * 
     * <p><b>重要更新 (v2.1.1):</b> 每个路由现在包含自己remoteEntry scope
     * 支持多插件聚合场景下每个插件有独立的前端入口</p>
     * 
     * @return 聚合后的 UI 契约 Map，如果没有任UI 配置则返回 null
     */
    public Map<String, Object> aggregateUiContracts() {
        List<PluginManifest> manifests = scanManifests();
        
        if (manifests.isEmpty()) {
            log.debug("[PluginManifestScanner] 没有发现插件 manifest，UI 契约为空");
            return null;
        }
        
        // 聚合所有路由（每个路由包含自己remoteEntry scope
        List<Map<String, Object>> allRoutes = new ArrayList<>();
        // 使用第一个有效的 remoteEntry/scope 作为默认值（向后兼容
        String defaultRemoteEntry = null;
        String defaultScope = null;
        
        for (PluginManifest manifest : manifests) {
            if (manifest.getUi() == null || manifest.getUi().getWeb() == null) {
                continue;
            }
            
            PluginManifest.WebUi webUi = manifest.getUi().getWeb();
            
            // 获取当前插件remoteEntry scope
            String pluginRemoteEntry = webUi.getRemoteEntry();
            String pluginScope = webUi.getScope();
            
            // 记录第一个有效值作为默认值（用于 web 对象的顶层字段，向后兼容
            if (defaultRemoteEntry == null && pluginRemoteEntry != null) {
                defaultRemoteEntry = pluginRemoteEntry;
            }
            if (defaultScope == null && pluginScope != null) {
                defaultScope = pluginScope;
            }
            
            // 转换路由，并remoteEntry scope 附加到每个路
            if (webUi.getRoutes() != null) {
                for (PluginManifest.WebRoute route : webUi.getRoutes()) {
                    Map<String, Object> routeMap = convertRoute(manifest.getName(), route);
                    
                    // v2.1.1: 每个路由携带自己remoteEntry scope
                    // 这是关键修复 - 支持多插件聚
                    if (pluginRemoteEntry != null) {
                        routeMap.put("remoteEntry", pluginRemoteEntry);
                    }
                    if (pluginScope != null) {
                        routeMap.put("scope", pluginScope);
                    }
                    
                    allRoutes.add(routeMap);
                    
                    log.debug("[PluginManifestScanner] 鑱氬悎璺敱: {} -> {} (scope: {}, entry: {})", 
                        manifest.getName(), route.getPath(), pluginScope, pluginRemoteEntry);
                }
            }
        }
        
        if (allRoutes.isEmpty()) {
            log.debug("[PluginManifestScanner] 没有有效UI 路由配置");
            return null;
        }
        
        // 如果没有 remoteEntry scope，则无法构成有效UI 契约
        // Plugin Engine 要求这些字段必填
        if (defaultRemoteEntry == null || defaultScope == null) {
            log.info("[PluginManifestScanner] UI 契约缺少必要字段 (remoteEntry/scope)，跳UI 注册");
            log.debug("[PluginManifestScanner] 如需注册 UI，请plugin-manifest.json 中配ui.web.remoteEntry ui.web.scope");
            return null;
        }
        
        // 过滤掉没有有menu.title 的路由（hidden 路由除外
        List<Map<String, Object>> validRoutes = new ArrayList<>();
        for (Map<String, Object> route : allRoutes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> menu = (Map<String, Object>) route.get("menu");
            if (menu == null) {
                validRoutes.add(route);
            } else if (Boolean.TRUE.equals(menu.get("hidden"))) {
                // 隐藏菜单不需title
                validRoutes.add(route);
            } else if (menu.get("title") != null) {
                validRoutes.add(route);
            } else {
                log.warn("[PluginManifestScanner] 路由 {} menu.title 为空，已跳过", route.get("path"));
            }
        }
        
        if (validRoutes.isEmpty()) {
            log.debug("[PluginManifestScanner] 没有有效UI 路由配置");
            return null;
        }
        
        // order 排序路由
        validRoutes.sort((a, b) -> {
            Integer orderA = getMenuOrder(a);
            Integer orderB = getMenuOrder(b);
            return orderA.compareTo(orderB);
        });
        
        // 构建最终的 UI 契约
        // 顶层 remoteEntry/scope 是默认值（向后兼容），每个路由也携带自己的
        Map<String, Object> web = new HashMap<>();
        web.put("remoteEntry", defaultRemoteEntry);
        web.put("scope", defaultScope);
        web.put("routes", validRoutes);
        
        Map<String, Object> ui = new HashMap<>();
        ui.put("web", web);
        
        log.info("[PluginManifestScanner] UI 契约聚合完成, {} 个路(来自 {} 个插", 
            validRoutes.size(), manifests.stream().filter(m -> m.getUi() != null && m.getUi().getWeb() != null).count());
        
        return ui;
    }
    
    /**
     * 获取所有插件名
     * 
     * @return 插件名称列表
     */
    public List<String> getPluginNames() {
        return scanManifests().stream()
            .map(PluginManifest::getName)
            .filter(Objects::nonNull)
            .toList();
    }
    
    /**
     * 获取指定插件manifest
     * 
     * @param pluginName 插件名称
     * @return 插件 manifest，如果未找到返回 null
     */
    public PluginManifest getManifest(String pluginName) {
        return scanManifests().stream()
            .filter(m -> pluginName.equals(m.getName()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 清除缓存（用于热重载场景
     */
    public void clearCache() {
        cachedManifests = null;
        log.debug("[PluginManifestScanner] 缓存已清");
    }
    
    /**
     * 转换路由配置Map
     */
    private Map<String, Object> convertRoute(String pluginName, PluginManifest.WebRoute route) {
        Map<String, Object> routeMap = new HashMap<>();
        routeMap.put("path", route.getPath());
        
        if (route.getComponent() != null) {
            routeMap.put("component", route.getComponent());
        }
        if (route.getExact() != null) {
            routeMap.put("exact", route.getExact());
        }
        
        // 添加插件标识
        routeMap.put("plugin", pluginName);
        
        // 转换菜单配置
        if (route.getMenu() != null) {
            Map<String, Object> menuMap = new HashMap<>();
            PluginManifest.Menu menu = route.getMenu();
            
            if (menu.getTitle() != null) {
                menuMap.put("title", menu.getTitle());
            }
            if (menu.getIcon() != null) {
                menuMap.put("icon", menu.getIcon());
            }
            if (menu.getOrder() != null) {
                menuMap.put("order", menu.getOrder());
            }
            if (menu.getParentId() != null) {
                menuMap.put("parentId", menu.getParentId());
            }
            if (menu.getHidden() != null) {
                menuMap.put("hidden", menu.getHidden());
            }
            
            routeMap.put("menu", menuMap);
        }
        
        return routeMap;
    }
    
    /**
     * 获取菜单排序
     */
    @SuppressWarnings("unchecked")
    private Integer getMenuOrder(Map<String, Object> routeMap) {
        if (routeMap.get("menu") instanceof Map) {
            Map<String, Object> menu = (Map<String, Object>) routeMap.get("menu");
            if (menu.get("order") instanceof Integer) {
                return (Integer) menu.get("order");
            }
        }
        return 999; // 默认排序
    }
}

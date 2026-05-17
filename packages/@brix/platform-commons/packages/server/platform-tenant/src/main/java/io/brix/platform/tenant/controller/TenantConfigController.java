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
package io.brix.platform.tenant.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.common.tenant.TenantContext;
import io.brix.platform.tenant.dto.TenantConfigDto;
import io.brix.platform.tenant.entity.TenantConfig;
import io.brix.platform.tenant.service.TenantSettingsService;
import jakarta.validation.Valid;

/**
 * Tenant Config REST Controller — plugin-level namespace configuration.
 *
 * <p>Provides APIs for managing key-value configuration entries scoped
 * per tenant and namespace. Used by plugins to persist tenant-specific settings.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — REST endpoint for namespace configuration.</p>
 *
 * <h3>API Endpoints</h3>
 * <ul>
 *   <li>GET    /api/v1/tenant/config/{namespace}             — List namespace configs</li>
 *   <li>PUT    /api/v1/tenant/config/{namespace}/{key}       — Create/update config</li>
 *   <li>DELETE /api/v1/tenant/config/{namespace}/{key}       — Delete config</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
@RestController
@RequestMapping("/api/v1/tenant/config")
public class TenantConfigController {

    private static final Logger log = LoggerFactory.getLogger(TenantConfigController.class);

    private final TenantSettingsService settingsService;

    public TenantConfigController(TenantSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Lists all config entries for a given namespace.
     */
    @GetMapping("/{namespace}")
    public ResponseEntity<?> getNamespaceConfigs(@PathVariable String namespace) {
        Long tenantId = requireTenantId();
        List<TenantConfig> configs = settingsService.getNamespaceConfigs(tenantId, namespace);

        List<Map<String, Object>> items = configs.stream()
                .map(this::toConfigResponse)
                .toList();

        return ResponseEntity.ok(Map.of("items", items, "namespace", namespace));
    }

    /**
     * Creates or updates a config entry.
     */
    @PutMapping("/{namespace}/{key}")
    public ResponseEntity<?> putConfig(@PathVariable String namespace,
                                       @PathVariable String key,
                                       @Valid @RequestBody TenantConfigDto dto) {
        Long tenantId = requireTenantId();
        settingsService.putConfig(tenantId, namespace, key, dto);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Deletes a config entry.
     */
    @DeleteMapping("/{namespace}/{key}")
    public ResponseEntity<?> deleteConfig(@PathVariable String namespace,
                                          @PathVariable String key) {
        Long tenantId = requireTenantId();
        settingsService.deleteConfig(tenantId, namespace, key);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    private Long requireTenantId() {
        return TenantContext.getTenantId()
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .orElseThrow(() -> new IllegalStateException("Tenant context not available"));
    }

    private Map<String, Object> toConfigResponse(TenantConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", config.getConfigKey());
        map.put("value", config.isSensitive() ? "***" : config.getConfigValue());
        map.put("type", config.getConfigType().name());
        map.put("description", config.getDescription());
        map.put("sensitive", config.isSensitive());
        map.put("readonly", config.isReadonly());
        map.put("updatedAt", config.getUpdatedAt());
        return map;
    }
}

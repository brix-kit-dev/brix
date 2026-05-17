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
package io.brix.platform.tenant.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO for tenant branding configuration.
 *
 * <p>Used by {@code GET/PUT /api/v1/tenant/branding}.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons DTO</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
public class TenantBrandingDto {

    @Size(max = 512)
    private String logoUrl;

    @Size(max = 512)
    private String faviconUrl;

    @Size(max = 20)
    private String primaryColor;

    @Size(max = 20)
    private String secondaryColor;

    @Size(max = 256)
    private String loginPageTitle;

    @Size(max = 512)
    private String loginPageSubtitle;

    @Size(max = 512)
    private String loginPageBgUrl;

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public String getLoginPageTitle() {
        return loginPageTitle;
    }

    public void setLoginPageTitle(String loginPageTitle) {
        this.loginPageTitle = loginPageTitle;
    }

    public String getLoginPageSubtitle() {
        return loginPageSubtitle;
    }

    public void setLoginPageSubtitle(String loginPageSubtitle) {
        this.loginPageSubtitle = loginPageSubtitle;
    }

    public String getLoginPageBgUrl() {
        return loginPageBgUrl;
    }

    public void setLoginPageBgUrl(String loginPageBgUrl) {
        this.loginPageBgUrl = loginPageBgUrl;
    }
}

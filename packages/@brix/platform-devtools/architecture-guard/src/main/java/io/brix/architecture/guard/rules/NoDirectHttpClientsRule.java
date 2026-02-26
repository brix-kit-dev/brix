/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import io.brix.architecture.guard.ArchitectureLayers;

/**
 * No Direct HTTP Clients Rule.
 *
 * <p>Cross-service calls must go through HttpCapability to ensure:
 * unified error handling, distributed tracing, service discovery, and load balancing.</p>
 *
 * @since 3.1.0
 */
public final class NoDirectHttpClientsRule {

    private NoDirectHttpClientsRule() {}

    public static ArchRule noRestTemplate() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.REST_TEMPLATE)
                .because("Direct RestTemplate usage is forbidden. Use HttpCapability for HTTP calls");
    }

    public static ArchRule noWebClient() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.WEB_CLIENT)
                .because("Direct WebClient usage is forbidden. Use HttpCapability for HTTP calls");
    }

    public static ArchRule noOpenFeign() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.OPEN_FEIGN)
                .because("Direct OpenFeign usage is forbidden. Use HttpCapability for HTTP calls");
    }

    public static ArchRule noOkHttp() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.OKHTTP)
                .because("Direct OkHttp usage is forbidden. Use HttpCapability for HTTP calls");
    }

    public static ArchRule noJdkHttpClient() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.JDK_HTTP_CLIENT)
                .because("Direct java.net.http.HttpClient usage is forbidden. Use HttpCapability for HTTP calls");
    }
}

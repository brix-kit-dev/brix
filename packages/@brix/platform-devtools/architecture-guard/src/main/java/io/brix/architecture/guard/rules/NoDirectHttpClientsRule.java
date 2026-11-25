/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Set;

import io.brix.architecture.guard.ArchitectureLayers;

/**
 * No Direct HTTP Clients Rule.
 *
 * <p>Calls to services outside the platform must go through ExternalHttpCapability to ensure:
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
                .because("Direct RestTemplate usage is forbidden. Use ExternalHttpCapability for HTTP calls");
    }

    public static ArchRule noWebClient() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.WEB_CLIENT)
                .because("Direct WebClient usage is forbidden. Use ExternalHttpCapability for HTTP calls");
    }

    public static ArchRule noOpenFeign() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.OPEN_FEIGN)
                .because("Direct OpenFeign usage is forbidden. Use ExternalHttpCapability for HTTP calls");
    }

    public static ArchRule noOkHttp() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.OKHTTP)
                .because("Direct OkHttp usage is forbidden. Use ExternalHttpCapability for HTTP calls");
    }

    /** Apache HttpClient 4 and 5, Feign, Retrofit, Netty and Reactor Netty, AsyncHttpClient, Jetty's client, Hutool. */
    private static final String[] OTHER_CLIENTS = {
        "org.apache.http..", "org.apache.hc..", "feign..", "retrofit2..", "io.netty..", "reactor.netty..",
        "org.asynchttpclient..", "org.eclipse.jetty.client..", "cn.hutool.http..",
        "org.springframework.http.client..", "org.springframework.web.service.."
    };

    /** The JDK's connections and sockets. */
    private static final String JDK_CONNECTIONS =
            "java\\.net\\.(URLConnection|HttpURLConnection|Socket|ServerSocket|DatagramSocket)"
            + "|javax\\.net\\.ssl\\.(HttpsURLConnection|SSLSocket|SSLServerSocket)"
            + "|java\\.nio\\.channels\\.((Asynchronous)?(Server)?Socket|Datagram)Channel";

    /** The methods of {@code java.net.URL} that open it; building and taking a URL apart opens nothing. */
    private static final Set<String> URL_OPENERS = Set.of("openConnection", "openStream", "getContent");

    /**
     * Refuses the HTTP clients the other rules do not name, Spring's request factories and HTTP interfaces, and the
     * JDK's connections, sockets and opened URLs (root P-3, A-1, A-24).
     *
     * @return the rule
     */
    public static ArchRule noOtherHttpClients() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(OTHER_CLIENTS)
                .orShould().dependOnClassesThat().haveNameMatching(JDK_CONNECTIONS)
                .orShould().accessTargetWhere(DescribedPredicate.<JavaAccess<?>>describe(
                        "the target opens a java.net.URL",
                        access -> "java.net.URL".equals(access.getTargetOwner().getFullName())
                                && URL_OPENERS.contains(access.getName())))
                .because("Direct HTTP clients, connections and sockets are forbidden. "
                        + "Use ExternalHttpCapability for HTTP calls");
    }

    public static ArchRule noJdkHttpClient() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.JDK_HTTP_CLIENT)
                .because("Direct java.net.http.HttpClient usage is forbidden. Use ExternalHttpCapability for HTTP calls");
    }
}

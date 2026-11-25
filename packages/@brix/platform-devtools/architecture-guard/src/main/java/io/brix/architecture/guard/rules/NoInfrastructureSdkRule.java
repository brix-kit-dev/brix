/*
 * Copyright 2026 Brix Authors
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
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * No infrastructure SDK in plugin code: a plugin holds no driver, connection, client or middleware SDK, and reaches
 * infrastructure through a capability whose L2C adapter holds them (root P-3, A-1, section 3.2).
 *
 * <p>{@link NoMiddlewareClientsRule} and {@link NoDirectHttpClientsRule} refuse message brokers, Redis and HTTP clients;
 * this rule refuses the object stores and cloud vendors' SDKs and the JDBC drivers and connection pools. The lists name
 * the Java packages the libraries have, which are not their Maven groups. A rule checks every class the module's test
 * imports, whatever its package, and fails when the test imports none (standard-link plan queue item 21, 21d, D178).</p>
 *
 * @author Brix Architecture Team
 * @since 3.3.0
 */
public final class NoInfrastructureSdkRule {

    /** MinIO, AWS (SDK 2 and 1), Azure, Google Cloud, Alibaba Cloud, Tencent Cloud, Huawei Cloud, Qiniu. */
    private static final String[] OBJECT_STORAGE_AND_CLOUD = {
        "io.minio..",
        "software.amazon.awssdk..", "com.amazonaws..",
        "com.azure..", "com.microsoft.azure..",
        "com.google.cloud..",
        "com.aliyun..", "com.aliyuncs..",
        "com.qcloud..", "com.tencentcloudapi..",
        "com.obs..", "com.huaweicloud..",
        "com.qiniu.."
    };

    /** The drivers of the databases a DCP may name, the test database's, and the connection pools. */
    private static final String[] JDBC_DRIVERS_AND_POOLS = {
        "org.postgresql..", "com.mysql..", "org.mariadb..", "oracle.jdbc..", "com.microsoft.sqlserver..", "org.h2..",
        "dm.jdbc..", "com.kingbase8..", "com.oceanbase..", "org.opengauss..",
        "com.zaxxer.hikari..", "com.alibaba.druid..", "org.apache.commons.dbcp2..", "org.apache.tomcat.jdbc..",
        "com.mchange.v2.c3p0.."
    };

    private NoInfrastructureSdkRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Refuses object stores' clients and cloud vendors' SDKs.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noObjectStorageOrCloudSdk() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(OBJECT_STORAGE_AND_CLOUD)
                .because("root P-3, A-1: a plugin holds no object store client or cloud SDK. " +
                        "Use FileStorageCapability; for another vendor service, an external service through ExternalHttpCapability");
    }

    /**
     * Refuses JDBC drivers and connection pools.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noJdbcDriverOrPool() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(JDBC_DRIVERS_AND_POOLS)
                .because("root P-3, A-1, A-13: a plugin holds no JDBC driver or connection pool. Use PersistenceCapability");
    }
}

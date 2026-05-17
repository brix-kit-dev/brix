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
package io.brix.devtools.keys;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Standalone CLI utility to export RSA keys from a PKCS#12 keystore to PEM format.
 *
 * <p>This class is a <b>deployment-time CLI tool</b> (not a Spring bean) used
 * during key provisioning. It has no runtime dependencies on the Brix platform
 * and uses only {@code java.base} / {@code java.security} APIs.</p>
 *
 * <h3>Migration History</h3>
 * <p>Migrated from {@code io.brix.enterprise.host.standalone.util.ExportKeys}
 * (enterprise-host/host-shell-standalone) to {@code io.brix.devtools.keys.ExportKeys}
 * (platform-devtools/key-export-tool) during Phase 3.9 of the Architecture Review.
 * Reason: CLI utilities must not reside in the Host startup module
 * (Blueprint Constraint 6 — Ultra-Thin Host).</p>
 *
 * <h3>Security</h3>
 * <p>The keystore password is read exclusively from the
 * {@code JWT_KEYSTORE_PASSWORD} environment variable — it is <b>never</b>
 * hardcoded in source code. The tool refuses to run if the variable is absent.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 *   # Set the keystore password as an environment variable
 *   export JWT_KEYSTORE_PASSWORD=your-secure-password
 *
 *   # Run the key export utility
 *   java -jar key-export-tool.jar path/to/jwt-keystore.p12 output/directory
 * </pre>
 *
 * <h3>Output Files</h3>
 * <ul>
 *   <li>{@code public.pem}  — RSA public key in PEM format</li>
 *   <li>{@code private.pem} — RSA private key in PEM format</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @version 3.1.0
 * @since 3.0.0
 */
public final class ExportKeys {

    /** Environment variable name for the keystore password. */
    private static final String ENV_KEYSTORE_PASSWORD = "JWT_KEYSTORE_PASSWORD";

    /** Key alias used in the PKCS#12 keystore. */
    private static final String KEY_ALIAS = "brix-jwt";

    private ExportKeys() {
        // Utility class — prevent instantiation
    }

    /**
     * Entry point for the key export CLI tool.
     *
     * <p>Expects exactly two positional arguments:</p>
     * <ol>
     *   <li>{@code keystore-path} — path to the PKCS#12 keystore file</li>
     *   <li>{@code output-dir}    — directory where PEM files will be written</li>
     * </ol>
     *
     * @param args command-line arguments
     * @throws Exception if keystore loading or key export fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -jar key-export-tool.jar <keystore-path> <output-dir>");
            System.err.println("  Requires environment variable: " + ENV_KEYSTORE_PASSWORD);
            System.exit(1);
        }

        String ksPath = args[0];
        String outDir = args[1];

        // Read keystore password from environment variable (never hardcode)
        String password = System.getenv(ENV_KEYSTORE_PASSWORD);
        if (password == null || password.isBlank()) {
            System.err.println("ERROR: Environment variable " + ENV_KEYSTORE_PASSWORD + " is not set.");
            System.err.println("  Set it before running this tool: export " + ENV_KEYSTORE_PASSWORD + "=<password>");
            System.exit(1);
        }

        char[] passwordChars = password.toCharArray();

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(ksPath)) {
            ks.load(fis, passwordChars);
        }

        // Export public key
        java.security.cert.Certificate cert = ks.getCertificate(KEY_ALIAS);
        if (cert == null) {
            System.err.println("ERROR: No certificate found for alias '" + KEY_ALIAS + "'");
            System.exit(1);
        }
        PublicKey pub = cert.getPublicKey();
        String pubPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pub.getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        try (FileOutputStream fos = new FileOutputStream(outDir + File.separator + "public.pem")) {
            fos.write(pubPem.getBytes());
        }

        // Export private key
        PrivateKey priv = (PrivateKey) ks.getKey(KEY_ALIAS, passwordChars);
        if (priv == null) {
            System.err.println("ERROR: No private key found for alias '" + KEY_ALIAS + "'");
            System.exit(1);
        }
        String privPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(priv.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        try (FileOutputStream fos = new FileOutputStream(outDir + File.separator + "private.pem")) {
            fos.write(privPem.getBytes());
        }

        System.out.println("Keys exported successfully from alias '" + KEY_ALIAS + "'!");
    }
}

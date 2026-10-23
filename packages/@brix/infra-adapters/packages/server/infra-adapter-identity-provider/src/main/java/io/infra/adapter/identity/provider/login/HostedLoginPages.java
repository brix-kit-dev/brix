package io.infra.adapter.identity.provider.login;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import io.infra.adapter.identity.provider.support.Secrets;

/**
 * Serves the hosted login UI module under {@code /auth/**} (frontend {@code BRX-FE-AUTH-004}).
 * The module is packaged on the classpath at {@code META-INF/brix/hosted-login/}. Pages get a
 * per-response CSP nonce for the UI library's style elements; scripts only load from this
 * origin and no third-party origin is allowed.
 *
 * @since 3.2.0
 */
@Controller
public class HostedLoginPages {

    static final String ROOT = "META-INF/brix/hosted-login/";
    static final String NONCE_PLACEHOLDER = "__BRIX_CSP_NONCE__";
    private static final Pattern ASSET = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
    private static final Map<String, MediaType> TYPES = Map.of(
            "js", MediaType.valueOf("text/javascript"),
            "css", MediaType.valueOf("text/css"),
            "svg", MediaType.valueOf("image/svg+xml"),
            "png", MediaType.IMAGE_PNG,
            "woff2", MediaType.valueOf("font/woff2"),
            "json", MediaType.APPLICATION_JSON);

    /** CSP of hosted login pages; {@code %s} is the response's nonce. */
    static final String PAGE_POLICY = "default-src 'self'; script-src 'self'; style-src 'self' 'nonce-%s'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; "
            + "form-action 'self'; base-uri 'none'; object-src 'none'";

    @GetMapping({"/auth", "/auth/login", "/auth/error", "/auth/signed-out"})
    public ResponseEntity<String> page() throws IOException {
        Resource index = new ClassPathResource(ROOT + "index.html");
        if (!index.exists()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("The hosted login UI module is not installed.");
        }
        String nonce = Secrets.randomToken(18);
        String html;
        try (InputStream in = index.getInputStream()) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace(NONCE_PLACEHOLDER, nonce);
        }
        return ResponseEntity.ok()
                .header("Content-Security-Policy", PAGE_POLICY.formatted(nonce))
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @GetMapping("/auth/assets/{name}")
    public ResponseEntity<Resource> asset(@PathVariable("name") String name) {
        int dot = name.lastIndexOf('.');
        MediaType type = dot < 0 ? null : TYPES.get(name.substring(dot + 1));
        Resource resource = new ClassPathResource(ROOT + "assets/" + name);
        if (!ASSET.matcher(name).matches() || type == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .contentType(type)
                .body(resource);
    }
}

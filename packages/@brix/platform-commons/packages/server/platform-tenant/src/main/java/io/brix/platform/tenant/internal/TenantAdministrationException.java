package io.brix.platform.tenant.internal;

/**
 * Stable internal-contract exception for tenant administration failures.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class TenantAdministrationException extends RuntimeException {

    private final String code;

    public TenantAdministrationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

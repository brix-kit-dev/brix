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
package io.runtime.sdk.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Request metadata policy for audit records (super-admin §5, root BRX-RS-DATA-047): a client
 * address keeps only its network prefix, a user agent is cut to a fixed length.
 *
 * <p>Only literal addresses are accepted, so masking never triggers a name lookup.</p>
 *
 * @since 3.3.0
 */
public final class RequestMetadataMasking {

    /** Longest user agent an audit record keeps. */
    public static final int USER_AGENT_MAX_LENGTH = 256;

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]{2,45}");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    private RequestMetadataMasking() {
    }

    /**
     * Keeps the network prefix of a client address: {@code /24} for IPv4 and {@code /48} for IPv6.
     *
     * @param address literal client address, possibly bracketed or with an IPv6 zone
     * @return the masked prefix in CIDR form, or {@code null} for a missing or non-literal address
     */
    public static String maskAddress(String address) {
        if (address == null) {
            return null;
        }
        String literal = address.trim();
        if (literal.startsWith("[") && literal.endsWith("]")) {
            literal = literal.substring(1, literal.length() - 1);
        }
        int zone = literal.indexOf('%');
        if (zone >= 0) {
            literal = literal.substring(0, zone);
        }
        boolean v4 = IPV4.matcher(literal).matches();
        if (!v4 && !(literal.indexOf(':') >= 0 && IPV6.matcher(literal).matches())) {
            return null;
        }
        InetAddress parsed;
        try {
            parsed = InetAddress.getByName(literal);
        } catch (UnknownHostException | SecurityException e) {
            return null;
        }
        byte[] bytes = parsed.getAddress();
        if (parsed instanceof Inet4Address) {
            return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + "." + (bytes[2] & 0xff) + ".0/24";
        }
        if (parsed instanceof Inet6Address) {
            return String.format("%x:%x:%x::/48",
                ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff),
                ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff),
                ((bytes[4] & 0xff) << 8) | (bytes[5] & 0xff));
        }
        return null;
    }

    /**
     * Removes control characters from a user agent and cuts it to {@value #USER_AGENT_MAX_LENGTH} characters.
     *
     * @param userAgent raw user agent
     * @return the kept part, or {@code null} when nothing is left
     */
    public static String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        String cleaned = CONTROL.matcher(userAgent).replaceAll("").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() <= USER_AGENT_MAX_LENGTH ? cleaned : cleaned.substring(0, USER_AGENT_MAX_LENGTH);
    }
}

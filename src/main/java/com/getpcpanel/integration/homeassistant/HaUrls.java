package com.getpcpanel.integration.homeassistant;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * URL checks for the configured Home Assistant servers. Plain HTTP is the norm for local HA
 * installs and must not trigger warnings; only sending an access token unencrypted across a
 * non-local network is worth flagging.
 */
public final class HaUrls {
    private HaUrls() {
    }

    /**
     * True only when {@code url} uses plain {@code http} <em>and</em> its host is not local.
     * Local means: localhost, loopback (127/8, ::1), RFC1918 (10/8, 172.16/12, 192.168/16),
     * link-local (169.254/16, fe80::/10), IPv6 unique-local (fc00::/7), hostnames ending in
     * {@code .local}/{@code .lan}/{@code .home.arpa}, and single-label hostnames (no dot).
     */
    public static boolean isNonLocalPlainHttp(@Nullable String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            return false;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        var host = uri.getHost();
        return host != null && !isLocalHost(host);
    }

    private static boolean isLocalHost(String host) {
        var h = StringUtils.stripEnd(host.toLowerCase(Locale.ROOT), ".");
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (h.contains(":")) { // IPv6 literal
            return "::1".equals(h) || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd");
        }
        var ipv4 = parseIpv4(h);
        if (ipv4 != null) {
            int a = ipv4[0], b = ipv4[1];
            return a == 127 || a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168) || (a == 169 && b == 254);
        }
        return "localhost".equals(h) || h.endsWith(".localhost")
                || !h.contains(".")
                || h.endsWith(".local") || h.endsWith(".lan")
                || "home.arpa".equals(h) || h.endsWith(".home.arpa");
    }

    @Nullable
    private static int[] parseIpv4(String host) {
        var parts = StringUtils.split(host, '.');
        if (parts == null || parts.length != 4) {
            return null;
        }
        var result = new int[4];
        for (var i = 0; i < 4; i++) {
            if (!StringUtils.isNumeric(parts[i]) || parts[i].length() > 3) {
                return null;
            }
            result[i] = Integer.parseInt(parts[i]);
            if (result[i] > 255) {
                return null;
            }
        }
        return result;
    }
}

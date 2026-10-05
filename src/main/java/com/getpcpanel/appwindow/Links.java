package com.getpcpanel.appwindow;

import java.net.URI;

import javax.annotation.Nullable;

/** Which links the app window follows itself and which it hands to the desktop. */
final class Links {
    private Links() {
    }

    /** Whether {@code url} belongs to the application served at {@code appUrl}, rather than to some other site. */
    static boolean sameOrigin(String appUrl, @Nullable String url) {
        if (url == null) {
            return false;
        }
        try {
            var app = URI.create(appUrl);
            var target = URI.create(url);
            return app.getScheme().equalsIgnoreCase(String.valueOf(target.getScheme()))
                    && app.getHost().equalsIgnoreCase(String.valueOf(target.getHost()))
                    && app.getPort() == target.getPort();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Whether {@code url} is something to hand to the desktop's default handler: a website or a mail address. */
    static boolean opensExternally(@Nullable String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("mailto:"));
    }
}

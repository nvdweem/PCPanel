package com.getpcpanel.util.version;

import java.util.regex.Pattern;

import org.apache.commons.lang3.math.NumberUtils;

public record Version(int id,
                      String html_url,
                      String name,
                      boolean prerelease) {
    static final String SNAPSHOT_POSTFIX = "-SNAPSHOT";
    private static final Pattern versionPattern = Pattern.compile("v([\\d.]+)");
    private static final Pattern buildPattern = Pattern.compile("\\((\\d+)\\)");

    public String getRawVersion() {
        var matcher = versionPattern.matcher(name);
        if (matcher.find()) {
            return matcher.group(1) + (prerelease ? SNAPSHOT_POSTFIX : "");
        }
        throw new IllegalStateException("'" + name + "' is not a parsable version name.");
    }

    public int getBuild() {
        if (prerelease) {
            var matcher = buildPattern.matcher(name);
            if (matcher.find()) {
                return NumberUtils.toInt(matcher.group(1), -1);
            }
        }
        return -1;
    }

    public String versionDisplay() {
        return getRawVersion() + (prerelease ? "-" + getBuild() : "");
    }
}

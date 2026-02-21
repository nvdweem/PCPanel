package com.getpcpanel.util.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import one.util.streamex.StreamEx;

public record Version(int id,
                      String html_url,
                      String name,
                      boolean prerelease,
                      SemVer semVer) {
    static final String SNAPSHOT_POSTFIX = "-SNAPSHOT";
    private static final Pattern versionPattern = Pattern.compile("v?([\\d.]+)(-\\S*)?");
    private static final Pattern buildPattern = Pattern.compile("\\((\\d+)\\)");

    public Version {
        //noinspection ConstantValue
        if (semVer == null && StringUtils.isNotBlank(name)) {
            semVer = SemVer.fromName(name);
        }
    }

    public Version(int id, String html_url, String name, boolean prerelease) {
        this(id, html_url, name, prerelease, SemVer.fromName(name));
    }

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

    public record SemVer(List<Integer> parts, String suffix) implements Comparable<SemVer> {
        public static final SemVer EMPTY = new SemVer(Collections.emptyList(), "");

        public static SemVer fromName(String name) {
            var matcher = versionPattern.matcher(name);
            if (!matcher.find()) {
                return EMPTY;
            }
            var buildNrMatcher = buildPattern.matcher(name);
            var buildNr = StreamEx.of(buildNrMatcher.find() ? NumberUtils.toInt(buildNrMatcher.group(1), -1) : null).nonNull();

            return StreamEx.of(matcher.group(1).split("\\."))
                           .map(s -> NumberUtils.toInt(s, 0))
                           .append(buildNr)
                           .toListAndThen(l -> new SemVer(l, StringUtils.defaultIfBlank(matcher.group(2), "")));
        }

        @Override
        public int compareTo(SemVer o) {
            var thisPartsItt = parts.iterator();
            var oPartsItt = o.parts.iterator();

            while (thisPartsItt.hasNext() && oPartsItt.hasNext()) {
                var cmp = Integer.compare(thisPartsItt.next(), oPartsItt.next());
                if (cmp != 0)
                    return cmp;
            }

            if (thisPartsItt.hasNext())
                return 1;
            if (oPartsItt.hasNext())
                return -1;

            return StringUtils.compareIgnoreCase(suffix, o.suffix);
        }

        public SemVer withBuild(int build) {
            var newParts = new ArrayList<>(parts);
            newParts.add(build);
            return new SemVer(newParts, suffix);
        }
    }
}

package com.getpcpanel.util.version;

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

    /**
     * A comparable version. {@code parts} is the numeric core only ({@code 2.0} -> {@code [2, 0]}); the build number is
     * kept separate in {@code build} so it orders pre-releases rather than becoming a core component. A non-blank
     * {@code suffix} (e.g. {@code -SNAPSHOT}) marks a pre-release, which per SemVer 2.0.0 precedence sorts BELOW the
     * matching final release: {@code 2.0-SNAPSHOT (90) < 2.0 < 2.1-SNAPSHOT (1)}. That is what lets a clean ".0" release
     * outrank its own snapshot builds instead of the other way around.
     */
    public record SemVer(List<Integer> parts, String suffix, int build) implements Comparable<SemVer> {
        public static final SemVer EMPTY = new SemVer(List.of(), "", -1);

        public static SemVer fromName(String name) {
            var matcher = versionPattern.matcher(name);
            if (!matcher.find()) {
                return EMPTY;
            }
            var core = StreamEx.of(matcher.group(1).split("\\."))
                               .map(s -> NumberUtils.toInt(s, 0))
                               .toList();
            var buildNrMatcher = buildPattern.matcher(name);
            var buildNr = buildNrMatcher.find() ? NumberUtils.toInt(buildNrMatcher.group(1), -1) : -1;
            return new SemVer(core, StringUtils.defaultIfBlank(matcher.group(2), ""), buildNr);
        }

        public boolean preRelease() {
            return StringUtils.isNotBlank(suffix);
        }

        @Override
        public int compareTo(SemVer o) {
            // Numeric core, missing components count as zero (2.0 == 2.0.0, and 2.0.1 > 2.0).
            var max = Math.max(parts.size(), o.parts.size());
            for (var i = 0; i < max; i++) {
                var a = i < parts.size() ? parts.get(i) : 0;
                var b = i < o.parts.size() ? o.parts.get(i) : 0;
                if (a != b)
                    return Integer.compare(a, b);
            }
            // Equal core: a pre-release ranks below the matching final release (SemVer 2.0.0 precedence).
            if (preRelease() != o.preRelease())
                return preRelease() ? -1 : 1;
            // Both pre-releases of the same core: the build number orders them.
            if (preRelease()) {
                var cmp = Integer.compare(build, o.build);
                if (cmp != 0)
                    return cmp;
            }
            return StringUtils.compareIgnoreCase(suffix, o.suffix);
        }

        public SemVer withBuild(int build) {
            return new SemVer(parts, suffix, build);
        }
    }
}

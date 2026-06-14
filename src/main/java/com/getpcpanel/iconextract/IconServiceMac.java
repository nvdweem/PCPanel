package com.getpcpanel.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.platform.MacBuild;
import com.getpcpanel.util.ProcessHelper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Extracts application icons by locating the .icns of the surrounding .app bundle and converting it with the
 * built-in sips tool. Results (including failures) are cached because the app picker requests an icon per tile
 * and the shell-outs must not repeat.
 */
@Log4j2
@ApplicationScoped
@MacBuild
@RequiredArgsConstructor
public class IconServiceMac implements IIconService {
    private final ProcessHelper processHelper;
    private final Map<String, Optional<BufferedImage>> cache = new ConcurrentHashMap<>();

    @Override
    public BufferedImage getIconForFile(int width, int height, File file) {
        try {
            var bundle = bundleRoot(file);
            if (bundle == null) {
                return null;
            }
            return cache.computeIfAbsent(bundle.getPath() + ":" + width + "x" + height,
                    key -> Optional.ofNullable(extractIcon(bundle, width, height))).orElse(null);
        } catch (Exception e) {
            log.debug("Unable to determine icon for {}", file, e);
            return null;
        }
    }

    private static @Nullable File bundleRoot(@Nullable File file) {
        for (var parent = file; parent != null; parent = parent.getParentFile()) {
            if (parent.getName().endsWith(".app")) {
                return parent;
            }
        }
        return null;
    }

    private @Nullable BufferedImage extractIcon(File bundle, int width, int height) {
        try {
            var icns = findIcns(bundle);
            return icns == null ? null : convertToImage(icns, width, height);
        } catch (Exception e) {
            log.debug("Unable to extract icon from {}", bundle, e);
            return null;
        }
    }

    private @Nullable File findIcns(File bundle) throws IOException, InterruptedException {
        var resources = new File(bundle, "Contents/Resources");
        var iconName = plistIconName(new File(bundle, "Contents/Info.plist"));
        if (iconName != null) {
            var icns = new File(resources, StringUtils.appendIfMissing(iconName, ".icns"));
            if (icns.isFile()) {
                return icns;
            }
        }
        var fallback = resources.listFiles((dir, name) -> name.endsWith(".icns"));
        return fallback == null || fallback.length == 0 ? null : fallback[0];
    }

    private @Nullable String plistIconName(File plist) throws IOException, InterruptedException {
        if (!plist.isFile()) {
            return null;
        }
        var output = run(5, "plutil", "-extract", "CFBundleIconFile", "raw", "-o", "-", plist.getAbsolutePath());
        return output == null ? null : StringUtils.trimToNull(new String(output, Charset.defaultCharset()));
    }

    private @Nullable BufferedImage convertToImage(File icns, int width, int height) throws IOException, InterruptedException {
        var png = Files.createTempFile("pcpanel-icon", ".png").toFile();
        try {
            var output = run(10, "sips", "-z", String.valueOf(height), String.valueOf(width),
                    "-s", "format", "png", icns.getAbsolutePath(), "--out", png.getAbsolutePath());
            return output == null ? null : ImageIO.read(png);
        } finally {
            Files.deleteIfExists(png.toPath());
        }
    }

    /**
     * Runs the command and returns its output, or {@code null} when it fails or does not exit within the timeout.
     * Hung processes are killed so they cannot leak. The expected outputs are tiny, so waiting before reading
     * cannot fill the pipe buffer.
     */
    private @Nullable byte[] run(int timeoutSeconds, String... command) throws IOException, InterruptedException {
        var process = processHelper.builder(command).redirectErrorStream(true).start();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return null;
        }
        return process.exitValue() == 0 ? process.getInputStream().readAllBytes() : null;
    }
}

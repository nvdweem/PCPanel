package com.getpcpanel.util.version;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Linux AppImage self-update. Runs the bundled {@code appimageupdatetool} against our own
 * {@code $APPIMAGE}; it reads the zsync update-information baked into the AppImage at build time, pulls
 * only the changed blocks from the release it points at, atomically replaces the file (keeping a
 * {@code .zs-old} backup), and we then relaunch the updated file.
 *
 * <p>Supported only when running as an AppImage — the runtime exports {@code $APPIMAGE} as the absolute
 * path of the outer {@code .AppImage} — and only when {@code appimageupdatetool} is bundled next to our
 * binary (the {@code .deb}/dev runs are not AppImages, so they fall back to the download link).
 * {@code reinstallCurrent()} runs the exact same path so the mechanism can be smoke-tested from the debug
 * page without a newer release existing (zsync finds every block locally and just re-validates + relaunches).
 */
@Log4j2
@ApplicationScoped
public class AppImageUpdater implements PlatformUpdater {
    // appimageupdatetool is itself distributed as an AppImage; bundled next to our binary and run with
    // APPIMAGE_EXTRACT_AND_RUN so it needs no FUSE on the user's machine.
    private static final String TOOL = "appimageupdatetool-x86_64.AppImage";

    @Override
    public boolean isSupported() {
        var appImage = System.getenv("APPIMAGE");
        return StringUtils.isNotBlank(appImage) && Files.isRegularFile(Path.of(appImage)) && tool() != null;
    }

    @Override
    public AutoUpdateService.UpdateTarget updateToLatest() throws Exception {
        return runUpdate();
    }

    @Override
    public AutoUpdateService.UpdateTarget reinstallCurrent() throws Exception {
        return runUpdate();
    }

    private AutoUpdateService.UpdateTarget runUpdate() throws Exception {
        var appImage = System.getenv("APPIMAGE");
        var tool = tool();
        if (StringUtils.isBlank(appImage) || tool == null) {
            throw new AutoUpdateService.UpdateException("AppImage self-update is only available when running as an AppImage.");
        }

        log.info("Updating AppImage {} via {}", appImage, tool);
        var pb = new ProcessBuilder(tool, appImage);
        pb.environment().put("APPIMAGE_EXTRACT_AND_RUN", "1");
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        var code = pb.start().waitFor();
        if (code != 0) {
            throw new AutoUpdateService.UpdateException("The AppImage updater exited with code " + code + ".");
        }

        // sh -c 'sleep 3; exec "$0"' <appimage> — $0 is the updated file; exec replaces the shell with it.
        UpdaterRestart.relaunchAndExit(List.of("sh", "-c", "sleep 3; exec \"$0\"", appImage));
        return new AutoUpdateService.UpdateTarget("the latest version", "");
    }

    /** The bundled appimageupdatetool next to our own executable, or null when it is not present. */
    private static String tool() {
        return ProcessHandle.current().info().command()
                            .map(Path::of).map(Path::getParent).filter(Objects::nonNull)
                            .map(dir -> dir.resolve(TOOL))
                            .filter(Files::isExecutable)
                            .map(Path::toString)
                            .orElse(null);
    }
}

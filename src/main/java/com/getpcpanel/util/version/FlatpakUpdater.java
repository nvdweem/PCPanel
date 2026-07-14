package com.getpcpanel.util.version;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Linux Flatpak self-update. Asks the host (via {@code flatpak-spawn --host}) to {@code flatpak update}
 * our own app id, which pulls the newest commit from the OSTree remote that the {@code .flatpakref} added
 * at install time, then relaunches on the host.
 *
 * <p>Supported whenever running inside the Flatpak sandbox ({@code $FLATPAK_ID} is set). An install from
 * the single-file {@code .flatpak} bundle has no remote, so {@code flatpak update} is simply a no-op there
 * — the update only does something for installs that came from the hosted repo via the {@code .flatpakref}.
 * {@code reinstallCurrent()} runs the same path (a no-op when already current) to smoke-test the wiring.
 */
@Log4j2
@ApplicationScoped
public class FlatpakUpdater implements PlatformUpdater {
    @Override
    public boolean isSupported() {
        return StringUtils.isNotBlank(System.getenv("FLATPAK_ID"));
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
        var appId = System.getenv("FLATPAK_ID");
        if (StringUtils.isBlank(appId)) {
            throw new AutoUpdateService.UpdateException("Flatpak self-update is only available inside the Flatpak sandbox.");
        }

        log.info("Updating Flatpak {} via the host", appId);
        var code = new ProcessBuilder("flatpak-spawn", "--host", "flatpak", "update", "-y", appId)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start().waitFor();
        if (code != 0) {
            throw new AutoUpdateService.UpdateException("flatpak update exited with code " + code + ".");
        }

        // Relaunch on the host so it survives the sandbox teardown when this instance exits.
        UpdaterRestart.relaunchAndExit(List.of("flatpak-spawn", "--host", "sh", "-c", "sleep 3; flatpak run \"$0\"", appId));
        return new AutoUpdateService.UpdateTarget("the latest version", "");
    }
}

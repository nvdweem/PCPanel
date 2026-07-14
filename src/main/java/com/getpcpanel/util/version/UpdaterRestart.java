package com.getpcpanel.util.version;

import java.util.List;

import io.quarkus.runtime.Quarkus;
import lombok.extern.log4j.Log4j2;

/**
 * Relaunch the app after a Linux in-place update. Unlike the Windows installer — which closes and
 * relaunches the app itself — the AppImage/Flatpak transports update the files but leave the running
 * process alone, so we must restart ourselves.
 *
 * <p>The recipe: start a small <em>detached</em> relauncher that sleeps a few seconds and then launches
 * the (now updated) app, then ask Quarkus to shut down after a short grace period. The sleep lets this
 * instance flush its HTTP response and release its single-instance lock ({@code FileChecker}) before the
 * new one starts, so the relaunch is not swallowed by the "focus the existing instance" path.
 */
@Log4j2
final class UpdaterRestart {
    private UpdaterRestart() {}

    /**
     * @param relauncher a fully-formed, self-delaying command (it must outlive this process). For an
     *                   AppImage this is a plain {@code sh -c 'sleep …; exec …'}; for a Flatpak it must be
     *                   host-spawned ({@code flatpak-spawn --host …}) so it survives the sandbox teardown.
     */
    static void relaunchAndExit(List<String> relauncher) {
        try {
            new ProcessBuilder(relauncher)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Exception e) {
            log.error("Could not schedule the post-update relaunch; the app will exit without restarting", e);
        }

        var exit = new Thread(() -> {
            try {
                Thread.sleep(1500); // let the REST response flush before tearing down
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            Quarkus.asyncExit(0);
        }, "post-update-exit");
        exit.setDaemon(true);
        exit.start();
    }
}

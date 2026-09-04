package com.getpcpanel.util.app;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.getpcpanel.profile.SaveService;
import com.getpcpanel.rest.model.dto.OnboardingDto;
import com.getpcpanel.util.version.UpdateSource;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Decides what (if anything) to show the user when the app starts, and opens the UI in the browser when
 * appropriate. Three triggers open the browser at startup:
 *
 * <ul>
 *   <li><b>First run</b> — no save existed, so one was just created. Shows the new-user welcome dialog.</li>
 *   <li><b>Installer launch</b> — started with {@code /postinstall} over an existing save. Shows the
 *       post-install/update dialog (changelog + the open-in-browser option).</li>
 *   <li><b>User preference</b> — {@link com.getpcpanel.profile.Save#isOpenBrowserOnStartup()} is on. No
 *       dialog, just opens the UI.</li>
 * </ul>
 *
 * <p>Precedence for the dialog: a brand-new save wins. So a first install run via the installer (new
 * save AND {@code /postinstall}) shows the welcome dialog, not the update dialog — the update dialog only
 * appears when {@code /postinstall} is given AND a save already existed.
 *
 * <p>The browser is otherwise NOT opened on startup (PCPanel is a tray app), preserving the
 * fire-and-forget behaviour for normal launches.
 */
@Log4j2
@ApplicationScoped
public class StartupOnboarding {
    static final String INTENT_NEW_USER = "new-user";
    static final String INTENT_POST_INSTALL = "post-install";
    static final String INTENT_NONE = "none";

    @Inject SaveService saveService;
    @Inject Event<Object> eventBus;

    @ConfigProperty(name = "pcpanel.postinstall", defaultValue = "false")
    boolean postInstall;

    // Set when the in-app auto-updater relaunched us after a silent update (installer arg /updated). Shows
    // the same "just updated" dialog as postInstall, but the browser is NOT opened — the UI that triggered
    // the update is already open and reconnects to pick this up.
    @ConfigProperty(name = "pcpanel.updated", defaultValue = "false")
    boolean updated;

    // Whether the OS started this process at logon rather than the user starting it (launch arg "quiet",
    // published by Main). Reported in the startup log line so a user's log says how PCPanel was launched.
    @ConfigProperty(name = "pcpanel.autostart", defaultValue = "false")
    boolean autostart;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String version;

    private volatile String intent = INTENT_NONE;

    void onStart(@Observes StartupEvent event) {
        var newSave = saveService.isNewSave();
        intent = newSave ? INTENT_NEW_USER : (postInstall || updated) ? INTENT_POST_INSTALL : INTENT_NONE;

        // After an auto-update the triggering UI is already open (it reconnects), so never open a second
        // browser tab — even if "open on startup" is on. First install / /postinstall still open it.
        var setting = saveService.get().isOpenBrowserOnStartup();
        var openBrowser = newSave || postInstall || (!updated && setting);
        // Logged on every start, whichever way it goes: this line and FileChecker's are what a reported
        // log has to answer "did PCPanel open the browser, and why" with.
        log.info("Startup UI decision: open={} (firstRun={}, postInstall={}, updated={}, autostart={}, setting={})",
                openBrowser, newSave, postInstall, updated, autostart, setting);
        if (openBrowser) {
            eventBus.fire(new ShowMainEvent());
        }
    }

    /** Current onboarding info for the UI. Read once on load; {@link #acknowledge()} clears the intent. */
    public OnboardingDto info() {
        return new OnboardingDto(intent, version, changelogUrl());
    }

    /** Mark the onboarding dialog as shown so it does not reappear on a refresh or in another tab. */
    public void acknowledge() {
        intent = INTENT_NONE;
    }

    /**
     * Link to this version's release notes. SNAPSHOT/dev builds map to the rolling
     * {@code latest-snapshot} pre-release (old snapshots are not kept, so the newest is the relevant
     * one); a concrete release version links to the releases listing, whose newest entry is that version.
     */
    private String changelogUrl() {
        var base = "https://github.com/" + UpdateSource.GITHUB_REPO + "/releases";
        var isSnapshot = StringUtils.isBlank(version) || StringUtils.containsIgnoreCase(version, "snapshot") || "dev".equals(version);
        return isSnapshot ? base + "/tag/latest-snapshot" : base;
    }
}

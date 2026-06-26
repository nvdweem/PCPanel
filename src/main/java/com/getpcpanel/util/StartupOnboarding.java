package com.getpcpanel.util;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.getpcpanel.profile.SaveService;
import com.getpcpanel.rest.model.dto.OnboardingDto;

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

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String version;

    @ConfigProperty(name = "pcpanel.github.user-and-repo", defaultValue = "nvdweem/PCPanel")
    String githubUserAndRepo;

    private volatile String intent = INTENT_NONE;

    void onStart(@Observes StartupEvent event) {
        var newSave = saveService.isNewSave();
        intent = newSave ? INTENT_NEW_USER : postInstall ? INTENT_POST_INSTALL : INTENT_NONE;

        var openBrowser = newSave || postInstall || saveService.get().isOpenBrowserOnStartup();
        if (openBrowser) {
            log.info("Opening the UI in the browser on startup (firstRun={}, postInstall={}, setting={})",
                    newSave, postInstall, saveService.get().isOpenBrowserOnStartup());
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
     * Link to this version's release notes. SNAPSHOT/dev builds map to the rolling {@code latest-main}
     * pre-release (old snapshots are not kept, so the newest is the relevant one); a concrete release
     * version links to the releases listing, whose newest entry is that version.
     */
    private String changelogUrl() {
        var repo = StringUtils.contains(githubUserAndRepo, '/') ? githubUserAndRepo : "nvdweem/PCPanel";
        var base = "https://github.com/" + repo + "/releases";
        var isSnapshot = StringUtils.isBlank(version) || StringUtils.containsIgnoreCase(version, "snapshot") || "dev".equals(version);
        return isSnapshot ? base + "/tag/latest-main" : base;
    }
}

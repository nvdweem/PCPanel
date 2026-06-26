package com.getpcpanel.rest.model.dto;

/**
 * One-time onboarding hint for the UI, fetched once on load.
 *
 * @param intent       which dialog to show: {@code "new-user"} (a save was just created),
 *                     {@code "post-install"} (launched by the installer over an existing save), or
 *                     {@code "none"}.
 * @param version      the running app version, shown in the dialog.
 * @param changelogUrl link to this version's release notes (the latest pre-release for SNAPSHOT builds).
 */
public record OnboardingDto(String intent, String version, String changelogUrl) {
}

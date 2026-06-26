package dev.niels.discord.model;

import org.apache.commons.lang3.StringUtils;

/** A Discord user as reported over the local IPC/RPC connection. {@code globalName} is the new
 *  display name (may be null on older accounts); {@code username} is the unique handle commands match on. */
public record DiscordUser(String id, String username, String globalName) {
    /** Best human-facing label: the global display name, else the unique username, else the id. */
    public String displayName() {
        return StringUtils.firstNonBlank(globalName, username, id);
    }
}

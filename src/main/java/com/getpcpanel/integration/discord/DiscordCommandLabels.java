package com.getpcpanel.integration.discord;

import com.getpcpanel.cpp.MuteType;

/**
 * Shared label fragments for the Discord commands. Deliberately NOT in {@code com.getpcpanel.discord.command}:
 * the typescript-generator emits every type under that package's classPattern, and this helper is not part
 * of the wire contract.
 */
public final class DiscordCommandLabels {
    private DiscordCommandLabels() {
    }

    public static String mute(MuteType type) {
        return switch (type) {
            case mute -> "Mute";
            case unmute -> "Unmute";
            case toggle -> "Toggle mute";
        };
    }

    public static String deafen(MuteType type) {
        return switch (type) {
            case mute -> "Deafen";
            case unmute -> "Undeafen";
            case toggle -> "Toggle deafen";
        };
    }
}

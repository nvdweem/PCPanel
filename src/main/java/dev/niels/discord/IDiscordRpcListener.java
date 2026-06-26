package dev.niels.discord;

import java.util.List;

import dev.niels.discord.model.DiscordUser;
import dev.niels.discord.model.DiscordVoiceSettings;
import dev.niels.discord.model.DiscordVoiceUser;

/** Callbacks fired on the IPC read thread for Discord RPC lifecycle and voice-state changes. */
public interface IDiscordRpcListener {
    /** The IPC pipe/socket is open and the HANDSHAKE READY dispatch arrived (not yet authenticated). */
    default void ready(DiscordUser self) {
    }

    /** AUTHENTICATE succeeded — voice commands are now permitted. */
    default void authenticated(DiscordUser self) {
    }

    /** The connection dropped (Discord closed, the pipe broke, or we disconnected). */
    default void connectionClosed() {
    }

    default void onError(Throwable t) {
    }

    /** The local user's own mute/deaf/input/output settings changed. */
    default void voiceSettingsUpdated(DiscordVoiceSettings settings) {
    }

    /** The local user joined/left/switched voice channel ({@code channelId} null = left all channels). */
    default void voiceChannelSelected(String channelId) {
    }

    /** The membership or per-user voice state of the current voice channel changed. */
    default void voiceUsersChanged(List<DiscordVoiceUser> users) {
    }
}

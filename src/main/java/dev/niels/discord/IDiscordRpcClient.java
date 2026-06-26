package dev.niels.discord;

import java.util.Collection;

import dev.niels.discord.model.DiscordUser;
import dev.niels.discord.model.DiscordVoiceSettings;
import dev.niels.discord.model.DiscordVoiceUser;

/**
 * Control surface of a Discord local IPC (RPC) client. Connection/authorization lifecycle lives on the
 * implementation; this interface is the voice-control surface plus the read-only state the UI needs.
 */
public interface IDiscordRpcClient {
    boolean isConnected();

    boolean isAuthenticated();

    void addListener(IDiscordRpcListener listener);

    void removeListener(IDiscordRpcListener listener);

    /** Self mic mute. */
    void setSelfMute(boolean mute);

    /** Self deafen (also silences your own output). */
    void setSelfDeaf(boolean deaf);

    /** Own microphone (input) volume, 0-100. */
    void setInputVolume(float volume);

    /** Own output volume, 0-200. */
    void setOutputVolume(float volume);

    /** How loudly you hear another user (local), 0-200. */
    void setUserVolume(String userId, float volume);

    /** Locally mute/unmute another user (only affects what you hear). */
    void setUserMute(String userId, boolean mute);

    /** Members of the voice channel you are currently in (empty when not in a voice channel). */
    Collection<DiscordVoiceUser> getVoiceUsers();

    DiscordVoiceSettings getVoiceSettings();

    DiscordUser getSelfUser();
}

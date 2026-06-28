package com.getpcpanel.cpp.linux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.Properties;

import com.getpcpanel.keyboard.command.CommandMedia.VolumeButton;

import lombok.extern.log4j.Log4j2;

/**
 * Linux media-key fallback for sessions with no X server (pure Wayland), where the {@code XF86Audio*}
 * keys {@link LinuxKeyboard} synthesises cannot be injected. Controls the active media player directly
 * through MPRIS on the session D-Bus — the same mechanism {@code playerctl} uses — so play/pause, next,
 * previous and stop keep working. {@code mute} is a <em>system-volume</em> action with no MPRIS
 * equivalent, so it is not handled here.
 *
 * <p>dbus-java is the same stack the tray and logind detection already use, so this works in the
 * GraalVM native image. A short-lived connection is opened per press (button presses are rare), and
 * any failure degrades to a logged warning rather than taking down the command handler.
 */
@Log4j2
final class LinuxMprisMediaControl {
    private static final String MPRIS_PREFIX = "org.mpris.MediaPlayer2.";
    private static final String MPRIS_PATH = "/org/mpris/MediaPlayer2";
    private static final String PLAYER_IFACE = "org.mpris.MediaPlayer2.Player";
    private static final AtomicBoolean muteWarned = new AtomicBoolean();

    private LinuxMprisMediaControl() {
    }

    static void sendMediaKey(VolumeButton button) {
        if (button == VolumeButton.mute) {
            // MPRIS controls a player, not the system mixer; there is no portable mute here.
            if (muteWarned.compareAndSet(false, true)) {
                log.warn("Media 'mute' has no MPRIS equivalent; cannot mute without an X server on this session");
            }
            return;
        }
        // Catch Throwable: a missing/unreachable D-Bus class surfaces as a LinkageError in the native
        // image, and media control is non-essential — it must never crash the command handler.
        try (var connection = DBusConnectionBuilder.forSessionBus()
                                                   .transportConfig()
                                                   .configureSasl()
                                                   // Resolve the uid ourselves so dbus-java never falls back to
                                                   // com.sun.security.auth.module.UnixSystem for SASL EXTERNAL (#86).
                                                   .withSaslUid(currentUid())
                                                   .back()
                                                   .back()
                                                   .build()) {
            var player = pickPlayer(connection);
            if (player.isEmpty()) {
                log.warn("No MPRIS media player found on the session bus; cannot send '{}'", button);
                return;
            }
            var target = connection.getRemoteObject(player.get(), MPRIS_PATH, MprisPlayer.class);
            switch (button) {
                case playPause -> target.PlayPause();
                case next -> target.Next();
                case prev -> target.Previous();
                case stop -> target.Stop();
                case mute -> { /* handled above */ }
            }
            log.debug("Sent MPRIS '{}' to {}", button, player.get());
        } catch (Throwable e) { // NOSONAR - intentionally broad; media control must never take down the app
            log.error("Unable to send media key '{}' via MPRIS", button, e);
        }
    }

    /** Picks the active player: one that is {@code Playing} if any, else the first one advertised. */
    private static Optional<String> pickPlayer(DBusConnection connection) throws Exception {
        var bus = connection.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
        List<String> players = Arrays.stream(bus.ListNames()).filter(n -> n.startsWith(MPRIS_PREFIX)).toList();
        if (players.isEmpty()) {
            return Optional.empty();
        }
        for (var name : players) {
            try {
                var props = connection.getRemoteObject(name, MPRIS_PATH, Properties.class);
                if ("Playing".equals(props.Get(PLAYER_IFACE, "PlaybackStatus"))) {
                    return Optional.of(name);
                }
            } catch (Exception e) { // NOSONAR - a player that hides PlaybackStatus is simply not preferred
                log.debug("Could not read PlaybackStatus from {}", name, e);
            }
        }
        return Optional.of(players.get(0));
    }

    /**
     * Resolve the current user's uid without {@code com.sun.security.auth.module.UnixSystem}, whose JNI
     * library is not reliably present in a native image (#86): the home directory is owned by the uid.
     */
    private static long currentUid() {
        try {
            var home = System.getProperty("user.home");
            if (home != null && Files.getAttribute(Path.of(home), "unix:uid") instanceof Integer uid) {
                return uid.longValue();
            }
        } catch (Exception e) { // NOSONAR - any failure falls back to 0
            log.debug("Could not determine uid via file attributes, falling back to 0", e);
        }
        return 0;
    }
}

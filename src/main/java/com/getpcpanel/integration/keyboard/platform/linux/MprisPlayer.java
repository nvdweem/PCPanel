package com.getpcpanel.integration.keyboard.platform.linux;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/**
 * Minimal binding to a media player's {@code org.mpris.MediaPlayer2.Player} interface (session bus),
 * used as the Linux media fallback when no X server is reachable (pure Wayland). Only the transport
 * verbs are bound — MPRIS has no system-mute concept, so {@code mute} is handled elsewhere.
 *
 * @see <a href="https://specifications.freedesktop.org/mpris-spec/latest/Player_Interface.html">MPRIS Player interface</a>
 */
@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
public interface MprisPlayer extends DBusInterface {
    void PlayPause();

    void Next();

    void Previous();

    void Stop();
}

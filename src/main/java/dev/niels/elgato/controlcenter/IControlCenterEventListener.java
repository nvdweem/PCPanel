package dev.niels.elgato.controlcenter;

import dev.niels.elgato.shared.IRpcListener;

public interface IControlCenterEventListener extends IRpcListener {
    default void deviceRemoved(String deviceId) {
    }

    default void deviceConfigurationChanged(String deviceId) {
    }
}

package dev.niels.elgato.controlcenter;

import dev.niels.elgato.controlcenter.impl.ControlCenterClientImpl;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ControlCenterClient extends ControlCenterClientImpl {
    public ControlCenterClient(boolean autoConnect) {
        super(autoConnect);
    }
}

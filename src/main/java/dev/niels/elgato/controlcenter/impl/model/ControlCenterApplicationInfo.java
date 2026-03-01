package dev.niels.elgato.controlcenter.impl.model;

public record ControlCenterApplicationInfo(String appID,
                                           int build,
                                           int interfaceRevision,
                                           String name,
                                           String version) {
}

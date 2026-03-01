package dev.niels.elgato.controlcenter.impl.model;

public record ControlCenterDevice(String deviceID,
                                  String firmwareVersion,
                                  int firmwareVersionBuild,
                                  String name,
                                  int type) {
}

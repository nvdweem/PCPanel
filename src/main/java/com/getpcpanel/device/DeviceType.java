package com.getpcpanel.device;

import java.util.Arrays;
import java.util.List;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public enum DeviceType {
    PCPANEL_RGB("PCPanel RGB", 1240, 60242, 4, 4, false),
    PCPANEL_MINI("PCPanel Mini", 1155, 41924, 4, 4, false),
    PCPANEL_PRO("PCPanel Pro", 1155, 41925, 9, 5, true);

    public static final List<DeviceType> ALL = Arrays.asList(values());

    private final String niceName;
    private final int vid;
    private final int pid;
    private final int analogCount;
    private final int buttonCount;
    private final boolean hasLogoLed;
}

package com.getpcpanel.device;

import java.util.Arrays;
import java.util.List;

public enum DeviceType {
    PCPANEL_MAPLE(-1, -1, 4, 4),
    PCPANEL_RGB(1240, 60242, 4, 4),
    PCPANEL_MINI(1155, 41924, 4, 4),
    PCPANEL_PRO(1155, 41925, 9, 5);

    public static final List<DeviceType> ALL = Arrays.asList(values());

    private final int vid;

    private final int pid;

    private final int analogCount;

    private final int buttonCount;

    DeviceType(int vid, int pid, int analogCount, int buttonCount) {
        this.vid = vid;
        this.pid = pid;
        this.analogCount = analogCount;
        this.buttonCount = buttonCount;
    }

    public int getVid() {
        return vid;
    }

    public int getPid() {
        return pid;
    }

    public int getAnalogCount() {
        return analogCount;
    }

    public int getButtonCount() {
        return buttonCount;
    }
}

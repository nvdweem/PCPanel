package com.getpcpanel.profile;

import lombok.Data;

@Data
public class KnobSetting {
    private int minTrim;
    private int maxTrim = 100;
    private boolean logarithmic;
    private String overlayIcon;
    private int buttonDebounce = 50;
}

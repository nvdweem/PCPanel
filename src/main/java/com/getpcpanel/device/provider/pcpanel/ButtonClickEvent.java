package com.getpcpanel.device.provider.pcpanel;

public record ButtonClickEvent(String serialNum, int button, boolean dblClick) {
}

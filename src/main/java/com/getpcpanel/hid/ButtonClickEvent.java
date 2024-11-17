package com.getpcpanel.hid;

public record ButtonClickEvent(String serialNum, int button, boolean dblClick) {
}

package com.getpcpanel.commands;

public record PCPanelControlEvent(String serialNum, int knob, Runnable cmd) {
}

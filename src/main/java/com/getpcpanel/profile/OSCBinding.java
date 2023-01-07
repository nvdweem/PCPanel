package com.getpcpanel.profile;

public record OSCBinding(String address, float min, float max, boolean toggle) {
    public static final OSCBinding EMPTY = new OSCBinding("", 0, 1, false);
}

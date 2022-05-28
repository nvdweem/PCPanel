package com.getpcpanel.cpp;

import java.util.function.Function;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum MuteType {
    mute(x -> true),
    unmute(x -> false),
    toggle(x -> !x);

    private final Function<Boolean, Boolean> convert;

    public boolean convert(boolean value) {
        return convert.apply(value);
    }
}

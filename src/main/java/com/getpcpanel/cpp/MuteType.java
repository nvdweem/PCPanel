package com.getpcpanel.cpp;

import java.util.function.Function;

import javax.annotation.Nullable;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum MuteType {
    mute(x -> true),
    unmute(x -> false),
    toggle(x -> !Boolean.TRUE.equals(x));

    private final Function<Boolean, Boolean> convert;

    public boolean convert(@Nullable Boolean value) {
        return convert.apply(value);
    }
}

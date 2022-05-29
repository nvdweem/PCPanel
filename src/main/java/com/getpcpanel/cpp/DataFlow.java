package com.getpcpanel.cpp;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DataFlow {
    dfRender(false, true), dfCapture(true, false), dfAll(true, true);
    private final boolean input;
    private final boolean output;

    public static DataFlow from(int ord) {
        return values()[ord];
    }
}

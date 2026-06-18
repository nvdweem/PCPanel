package com.getpcpanel.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Util.parseColorComponents")
class UtilColorTest {

    @Test
    @DisplayName("parses #rrggbb and bare rrggbb hex")
    void parsesValidHex() {
        assertArrayEquals(new int[] { 255, 136, 0 }, Util.parseColorComponents("#ff8800"));
        assertArrayEquals(new int[] { 255, 136, 0 }, Util.parseColorComponents("ff8800"));
        assertArrayEquals(new int[] { 0, 0, 0 }, Util.parseColorComponents("#000000"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "  ", "nothex", "#fff", "12345", "#gggggg" })
    @DisplayName("returns null for null/blank/malformed values")
    void returnsNullForInvalid(String value) {
        assertNull(Util.parseColorComponents(value));
    }
}

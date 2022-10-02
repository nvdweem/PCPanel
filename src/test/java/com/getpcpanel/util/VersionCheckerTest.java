package com.getpcpanel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VersionCheckerTest {
    private VersionChecker sut;

    @BeforeEach
    void setUp() {
        sut = new VersionChecker(null, null);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1|1|false",
            "1.1|1.1|false",
            "1.1.1|1.1.1|false",
            "1|0.9|false",
            "1.1|1.0|false",
            "1.1.1|1|false",
            "1.1.1|1.0|false",
            "1.1.1|1.0.0|false",
            "1.2|1.1.1|false",
            "1.2-SNAPSHOT|1.1.1|false",
            "1.2-SNAPSHOT|1.2|true",
    })
    void isVersionNewer(String current, String latest, boolean shouldBeNewer) {
        assertEquals(shouldBeNewer, sut.isVersionNewer(current, latest));
        if (!StringUtils.equals(current, latest) && !StringUtils.contains(current, '-')) {
            assertEquals(!shouldBeNewer, sut.isVersionNewer(latest, current));
        }
    }
}

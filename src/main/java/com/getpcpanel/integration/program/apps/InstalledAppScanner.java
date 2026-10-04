package com.getpcpanel.integration.program.apps;

import java.util.List;

/** Lists the apps installed on this desktop. One build-time implementation per platform. */
@FunctionalInterface
public interface InstalledAppScanner {
    List<InstalledApp> scan();
}

package com.getpcpanel.alerts.platform.windows;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.alerts.MicUsage;
import com.getpcpanel.platform.WindowsBuild;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Reads the records Windows keeps for its own "microphone in use" icon: under {@code ConsentStore\microphone}, an
 * app whose {@code LastUsedTimeStop} is 0 (and that has started) is using a microphone now. Desktop apps sit under
 * {@code NonPackaged}, named by their path with {@code #} for {@code \}; Store apps directly, by app id.
 */
@Log4j2
@WindowsBuild
@ApplicationScoped
class WindowsMicUsage implements MicUsage {
    private static final String BASE = "Software\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\microphone";
    private static final String NON_PACKAGED = "NonPackaged";

    @Override
    public Set<String> appsUsingMic() {
        var result = new HashSet<String>();
        try {
            collect(BASE, false, result);
            collect(BASE + "\\" + NON_PACKAGED, true, result);
        } catch (RuntimeException e) {
            log.debug("Unable to read microphone usage", e);
        }
        return result;
    }

    private static void collect(String path, boolean desktop, Set<String> into) {
        if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, path)) {
            return;
        }
        for (var name : Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, path)) {
            if (!desktop && NON_PACKAGED.equals(name)) {
                continue;
            }
            var key = path + "\\" + name;
            if (inUse(key)) {
                into.add(desktop ? StringUtils.substringAfterLast("#" + name, "#").toLowerCase() : name.toLowerCase());
            }
        }
    }

    private static boolean inUse(String key) {
        try {
            var values = Advapi32Util.registryGetValues(WinReg.HKEY_CURRENT_USER, key);
            return values.get("LastUsedTimeStop") instanceof Long stop && stop == 0
                    && values.get("LastUsedTimeStart") instanceof Long start && start > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }
}

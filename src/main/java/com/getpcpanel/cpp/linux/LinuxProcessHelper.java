package com.getpcpanel.cpp.linux;

import java.io.IOException;
import java.nio.charset.Charset;

import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import com.getpcpanel.spring.ConditionalOnLinux;
import com.getpcpanel.util.ProcessHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@ConditionalOnLinux
@RequiredArgsConstructor
public class LinuxProcessHelper {
    private final ProcessHelper processHelper;

    public ProcessBuilder builder(String... command) {
        return processHelper.builder(command);
    }

    public int getActiveProcessPid() {
        try {
            var activeWindow = lineFrom("xdotool", "getactivewindow");
            if (StringUtils.isBlank(activeWindow)) {
                return -1;
            }
            return NumberUtils.toInt(lineFrom("xdotool", "getwindowpid", activeWindow), -1);
        } catch (Exception e) {
            log.error("Unable to run process", e);
        }
        return -1;
    }

    public @Nullable String getActiveProcess() {
        try {
            var pid = getActiveProcessPid();
            if (pid == -1)
                return null;
            return lineFrom("ps", "-p", String.valueOf(pid), "-o", "comm=");
        } catch (Exception e) {
            log.error("Unable to run process", e);
        }
        return null;
    }

    private @Nullable String lineFrom(String... cmd) throws IOException {
        var lines = IOUtils.readLines(processHelper.builder(cmd).start().getInputStream(), Charset.defaultCharset());
        if (lines.isEmpty()) {
            return null;
        }
        return lines.get(0);
    }
}

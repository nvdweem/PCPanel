package com.getpcpanel.cpp.linux;

import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import com.getpcpanel.spring.ConditionalOnLinux;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@ConditionalOnLinux
public class ProcessHelper {
    public String getActiveProcess() {
        try {
            var activeWindow = lineFrom("xdotool", "getactivewindow");
            var pid = lineFrom("xdotool", "getwindowpid", activeWindow);
            return lineFrom("ps", "-p", pid, "-o", "comm=");
        } catch (Exception e) {
            log.error("Unable to run process", e);
        }
        return null;
    }

    private String lineFrom(String... cmd) throws IOException {
        return IOUtils.readLines(new ProcessBuilder(cmd).start().getInputStream(), Charset.defaultCharset()).get(0);
    }
}

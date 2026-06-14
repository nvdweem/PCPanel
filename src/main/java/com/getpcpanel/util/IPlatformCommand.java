package com.getpcpanel.util;

import java.io.File;
import java.io.IOException;

import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.platform.MacBuild;
import com.getpcpanel.platform.WindowsBuild;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.linux.LinuxProcessHelper;
import com.getpcpanel.cpp.osx.OsxProcessHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class IPlatformCommand {
    public static final String FOCUS = "FOCUS";
    protected static final Runtime rt = Runtime.getRuntime();

    public abstract void exec(String shortcut);

    public abstract void kill(String process);

    @ApplicationScoped
    @LinuxBuild
    public static class LinuxPlatformCommand extends IPlatformCommand {
        @Inject
        LinuxProcessHelper processHelper;

        @Override
        public void exec(String shortcut) {
            try {
                var file = new File(shortcut);
                if (file.isDirectory()) {
                    processHelper.builder("gio", "open", shortcut).start();
                } else {
                    rt.exec(shortcut);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void kill(String process) {
            try {
                if (FOCUS.equals(process)) {
                    processHelper.builder("kill", String.valueOf(processHelper.getActiveProcessPid())).start();
                } else {
                    processHelper.builder("pkill", process).start();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @ApplicationScoped
    @MacBuild
    @RequiredArgsConstructor
    public static class OsxPlatformCommand extends IPlatformCommand {
        private final OsxProcessHelper processHelper;

        @Override
        public void exec(String shortcut) {
            try {
                var file = new File(shortcut);
                if (file.exists()) {
                    rt.exec(new String[] { "/usr/bin/open", file.getAbsolutePath() });
                } else {
                    rt.exec(shortcut);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void kill(String process) {
            if (FOCUS.equals(process)) {
                var app = processHelper.getFrontmostApp();
                if (app != null) {
                    try {
                        rt.exec(new String[] { "/bin/kill", String.valueOf(app.pid()) });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                // pkill matches by unanchored regex which over-matches (or fails on names with parentheses),
                // so match the exact executable name that the application picker stored
                var name = new File(process).getName();
                ProcessHandle.allProcesses()
                             .filter(ph -> ph.info().command().map(cmd -> new File(cmd).getName().equals(name)).orElse(false))
                             .forEach(ProcessHandle::destroy);
            }
        }
    }

    @ApplicationScoped
    @WindowsBuild
    @RequiredArgsConstructor
    public static class WindowsPlatformCommand extends IPlatformCommand {
        private final ISndCtrl sndCtrl;

        @Override
        public void exec(String shortcut) {
            var file = new File(shortcut);
            try {
                if (file.isDirectory()) {
                    Runtime.getRuntime().exec("cmd /c \"start %s\"".formatted(file.getAbsolutePath()));
                } else if (file.isFile() && Util.isFileExecutable(file)) {
                    rt.exec("cmd.exe /c \"" + file.getName() + "\"", null, file.getParentFile());
                } else {
                    rt.exec("cmd.exe /c \"" + shortcut + "\"");
                }
            } catch (IOException e) {
                log.error("Unable to run {}", shortcut, e);
            }
        }

        @Override
        public void kill(String process) {
            var toKill = stripFile(FOCUS.equals(process) ? sndCtrl.getFocusApplication() : process);
            try {
                rt.exec("cmd.exe /c taskkill /IM " + toKill + " /F");
            } catch (IOException e) {
                log.error("Unable to end '{}'", toKill, e);
            }
        }

        private String stripFile(String file) {
            return new File(file).getName();
        }
    }
}

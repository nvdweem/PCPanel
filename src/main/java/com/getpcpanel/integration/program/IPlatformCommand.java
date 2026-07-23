package com.getpcpanel.integration.program;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.platform.MacBuild;
import com.getpcpanel.platform.WindowsBuild;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.platform.process.LinuxProcessHelper;
import com.getpcpanel.platform.process.OsxProcessHelper;
import com.getpcpanel.util.Util;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class IPlatformCommand {
    public static final String FOCUS = "FOCUS";
    protected static final Runtime rt = Runtime.getRuntime();

    public abstract void exec(String shortcut);

    public abstract void kill(String process);

    @ApplicationScoped
    @Unremovable
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
    @Unremovable
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
    @Unremovable
    @WindowsBuild
    @RequiredArgsConstructor
    public static class WindowsPlatformCommand extends IPlatformCommand {
        private final ISndCtrl sndCtrl;

        // Extensions the OS can start directly via CreateProcess (ProcessBuilder). Everything else the
        // application picker accepts as "executable" (.lnk/.bat/.cmd/.msi/.ps1/.vbs/...) needs the shell
        // to resolve a file association or interpreter, so those keep the cmd.exe path below.
        private static final Set<String> DIRECTLY_LAUNCHABLE = Set.of("exe", "com");

        @Override
        public void exec(String shortcut) {
            var file = new File(shortcut);
            try {
                if (file.isDirectory()) {
                    // Open the folder in Explorer without a shell, so spaces / & / % / ^ in the path are
                    // taken literally (cmd's "start" also mis-reads a quoted path as a window title).
                    new ProcessBuilder(directoryArgv(file)).start();
                } else if (file.isFile() && Util.isFileExecutable(file)) {
                    if (canLaunchDirectly(file)) {
                        // A concrete .exe/.com the user pointed at: CreateProcess it directly. No shell
                        // means the whole path is one argument, so metacharacters pass through verbatim.
                        new ProcessBuilder(executableArgv(file)).directory(file.getParentFile()).start();
                    } else {
                        // .lnk/.bat/.msi/scripts can't be CreateProcess'd; the shell resolves their file
                        // association/interpreter. Run from the parent dir by bare name as before.
                        rt.exec("cmd.exe /c \"" + file.getName() + "\"", null, file.getParentFile());
                    }
                } else {
                    // Free-form input: a bare program name resolved via PATH, a URL / protocol handler, or a
                    // full command line with arguments the user typed. The shell is doing real work here
                    // (PATH lookup, argument parsing, ShellExecute of URLs), so it stays. The binding comes
                    // from the trusted local user, so this is a robustness choice, not an injection boundary.
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
                // taskkill.exe is a real executable — run it directly instead of via cmd.exe.
                new ProcessBuilder("taskkill", "/IM", toKill, "/F").start();
            } catch (IOException e) {
                log.error("Unable to end '{}'", toKill, e);
            }
        }

        private String stripFile(String file) {
            return new File(file).getName();
        }

        static boolean canLaunchDirectly(File file) {
            return DIRECTLY_LAUNCHABLE.contains(StringUtils.lowerCase(FilenameUtils.getExtension(file.getName())));
        }

        static List<String> directoryArgv(File dir) {
            return List.of("explorer.exe", dir.getAbsolutePath());
        }

        static List<String> executableArgv(File exe) {
            return List.of(exe.getAbsolutePath());
        }
    }
}

package com.getpcpanel.integration.program;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

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
import com.getpcpanel.util.os.FlatpakHost;
import com.getpcpanel.util.os.ProcessHelper;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.WinUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class IPlatformCommand {
    public static final String FOCUS = "FOCUS";
    /**
     * A URL ({@code https://…}, {@code mailto:…}, {@code ms-settings:…}): opened by whatever app handles the scheme. A
     * scheme has at least two characters, so a Windows drive path ({@code C:\…}) is not one.
     */
    private static final Pattern URL = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]+:.*");

    /** Runs a program or a command line. */
    public abstract void exec(String shortcut);

    /** Opens a website, folder or document in the app the desktop has for it. */
    public abstract void open(String target);

    public abstract void kill(String process);

    /** Whether {@code file} is a program to run rather than a document to open. */
    protected abstract boolean isExecutable(File file);

    /** Opens websites, folders and documents in their default app; runs programs and command lines. */
    public void openOrRun(String target) {
        if (opensWithDefaultApp(target)) {
            open(target);
        } else {
            exec(target);
        }
    }

    private boolean opensWithDefaultApp(String target) {
        if (URL.matcher(target).matches()) {
            return true;
        }
        var file = new File(target);
        return file.isDirectory() || (file.isFile() && !isExecutable(file));
    }

    @ApplicationScoped
    @Unremovable
    @LinuxBuild
    public static class LinuxPlatformCommand extends IPlatformCommand {
        @Inject
        LinuxProcessHelper processHelper;
        @Inject
        ProcessHelper processes;

        private static final String DESKTOP_ENTRY_LAUNCH = "gio launch \"$1\" 2>/dev/null || gtk-launch \"$(basename \"$1\" .desktop)\"";

        /** An installed app's desktop entry is launched as the app, not opened as a text file. */
        @Override
        public void openOrRun(String target) {
            if (isDesktopEntry(target)) {
                try {
                    processes.launch(desktopEntryLaunch(target));
                } catch (IOException e) {
                    log.error("Unable to launch {}", target, e);
                }
            } else {
                super.openOrRun(target);
            }
        }

        static boolean isDesktopEntry(String target) {
            return target.startsWith("/") && StringUtils.endsWithIgnoreCase(target, ".desktop");
        }

        /**
         * The entry lives on the host (the installed-app list is read there), so the host launches it: with
         * {@code gio launch}, else by its id with {@code gtk-launch} for a GLib without that command.
         */
        static String[] desktopEntryLaunch(String desktopFile) {
            return FlatpakHost.command("sh", "-c", DESKTOP_ENTRY_LAUNCH, "sh", desktopFile);
        }

        @Override
        public void exec(String shortcut) {
            try {
                var file = new File(shortcut);
                if (file.isDirectory()) {
                    processes.launch("gio", "open", shortcut);
                } else {
                    processes.launch(ProcessHelper.splitCommandLine(shortcut));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void open(String target) {
            try {
                // The desktop's handlers live on the host, so the Flatpak asks the host to open it.
                processes.launch(FlatpakHost.command("xdg-open", target));
            } catch (IOException e) {
                log.error("Unable to open {}", target, e);
            }
        }

        @Override
        protected boolean isExecutable(File file) {
            return file.canExecute();
        }

        @Override
        public void kill(String process) {
            try {
                if (FOCUS.equals(process)) {
                    processes.launch("kill", String.valueOf(processHelper.getActiveProcessPid()));
                } else {
                    processes.launch("pkill", process);
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
        private final ProcessHelper processes;

        @Override
        public void exec(String shortcut) {
            try {
                var file = new File(shortcut);
                if (file.exists()) {
                    processes.launch("/usr/bin/open", file.getAbsolutePath());
                } else {
                    processes.launch(ProcessHelper.splitCommandLine(shortcut));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void open(String target) {
            try {
                processes.launch("/usr/bin/open", target);
            } catch (IOException e) {
                log.error("Unable to open {}", target, e);
            }
        }

        @Override
        protected boolean isExecutable(File file) {
            return file.canExecute();
        }

        @Override
        public void kill(String process) {
            if (FOCUS.equals(process)) {
                var app = processHelper.getFrontmostApp();
                if (app != null) {
                    try {
                        processes.launch("/bin/kill", String.valueOf(app.pid()));
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
        private final ProcessHelper processes;

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
                    processes.launch(directoryArgv(file).toArray(String[]::new));
                } else if (file.isFile() && Util.isFileExecutable(file)) {
                    if (canLaunchDirectly(file)) {
                        // A concrete .exe/.com the user pointed at: CreateProcess it directly. No shell
                        // means the whole path is one argument, so metacharacters pass through verbatim.
                        processes.launch(file.getParentFile(), executableArgv(file).toArray(String[]::new));
                    } else {
                        // .lnk/.bat/.msi/scripts can't be CreateProcess'd: ShellExecute opens them as a double-click
                        // does (a shortcut with its own target, arguments and folder). cmd.exe can't: it does not run
                        // a .lnk ("not recognized as a command").
                        shellExecute(file.getAbsolutePath(), file.getParent());
                    }
                } else {
                    // Free-form input: a bare program name resolved via PATH, a URL / protocol handler, or a
                    // full command line with arguments the user typed. The shell is doing real work here
                    // (PATH lookup, argument parsing, ShellExecute of URLs), so it stays. The binding comes
                    // from the trusted local user, so this is a robustness choice, not an injection boundary.
                    processes.launch(ProcessHelper.splitCommandLine("cmd.exe /c \"" + shortcut + "\""));
                }
            } catch (IOException e) {
                log.error("Unable to run {}", shortcut, e);
            }
        }

        @Override
        public void open(String target) {
            shellExecute(target, null);
        }

        private static void shellExecute(String target, @Nullable String directory) {
            // ShellExecute values at or below 32 are error codes.
            var result = Shell32.INSTANCE.ShellExecute(null, "open", target, null, directory, WinUser.SW_SHOWNORMAL);
            if (result.longValue() <= 32) {
                log.error("Unable to open {} (ShellExecute error {})", target, result.longValue());
            }
        }

        @Override
        protected boolean isExecutable(File file) {
            return Util.isFileExecutable(file);
        }

        @Override
        public void kill(String process) {
            var toKill = stripFile(FOCUS.equals(process) ? sndCtrl.getFocusApplication() : process);
            try {
                // taskkill.exe is a real executable — run it directly instead of via cmd.exe.
                processes.launch("taskkill", "/IM", toKill, "/F");
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

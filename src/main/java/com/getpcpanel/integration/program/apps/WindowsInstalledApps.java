package com.getpcpanel.integration.program.apps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.platform.WindowsBuild;
import com.getpcpanel.util.os.ProcessHelper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * The Start menu's shortcuts, the user's and all users', that start a program, and the Microsoft Store apps. A Store
 * app has no shortcut file and its program cannot be started directly (access denied), so it is opened by its app id
 * ({@code shell:AppsFolder\<id>}), as the Start menu does.
 */
@Log4j2
@ApplicationScoped
@WindowsBuild
public class WindowsInstalledApps implements InstalledAppScanner {
    static final String APPS_FOLDER = "shell:AppsFolder\\";
    /**
     * The Start menu's packaged apps (an id with {@code !}) as JSON: name, app id and the program its manifest names
     * (for the window and the icon), read from the package's {@code AppxManifest.xml}.
     */
    private static final String STORE_APPS_SCRIPT = """
            $ErrorActionPreference = 'SilentlyContinue'
            $pkgs = @{}
            Get-AppxPackage | ForEach-Object { $pkgs[$_.PackageFamilyName] = $_.InstallLocation }
            $apps = Get-StartApps | Where-Object { $_.AppID -like '*!*' } | ForEach-Object {
                $family, $id = $_.AppID.Split('!', 2)
                $dir = $pkgs[$family]
                $exe = $null
                if ($dir) {
                    [xml]$m = Get-Content -LiteralPath (Join-Path $dir 'AppxManifest.xml') -Raw
                    $app = $m.Package.Applications.Application | Where-Object { $_.Id -eq $id } | Select-Object -First 1
                    if ($app.Executable) { $exe = Join-Path $dir $app.Executable }
                }
                [pscustomobject]@{ name = $_.Name; id = $_.AppID; exe = $exe }
            }
            [Console]::OutputEncoding = [Text.Encoding]::UTF8
            ConvertTo-Json -InputObject @($apps) -Compress
            """;

    @Inject ProcessHelper processes;
    @Inject ObjectMapper mapper;

    @Override
    public List<InstalledApp> scan() {
        var apps = new ArrayList<InstalledApp>();
        for (var root : Stream.of(System.getenv("APPDATA"), System.getenv("ProgramData")).filter(StringUtils::isNotBlank).map(d -> Path.of(d, "Microsoft", "Windows", "Start Menu", "Programs")).toList()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            shortcuts(root).stream().map(WindowsInstalledApps::read).filter(Objects::nonNull).forEach(apps::add);
        }
        apps.addAll(storeApps());
        return apps;
    }

    private List<InstalledApp> storeApps() {
        try {
            var script = Base64.getEncoder().encodeToString(STORE_APPS_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            var result = processes.run(Duration.ofSeconds(30), "powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", script);
            if (!result.succeeded()) {
                log.debug("Unable to list the Store apps: {}", result.stderr());
                return List.of();
            }
            return storeApps(mapper.readTree(String.join("", result.stdout())));
        } catch (IOException | RuntimeException e) {
            log.debug("Unable to list the Store apps", e);
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /** The Store apps in {@link #STORE_APPS_SCRIPT}'s output; one without a program is left out (no window to find). */
    static List<InstalledApp> storeApps(@Nullable JsonNode json) {
        var apps = new ArrayList<InstalledApp>();
        if (json == null || !json.isArray()) {
            return apps;
        }
        for (var node : json) {
            var name = node.path("name").asText("");
            var id = node.path("id").asText("");
            var exe = node.path("exe").asText("");
            if (name.isBlank() || id.isBlank() || exe.isBlank() || StringUtils.containsIgnoreCase(name, "uninstall")) {
                continue;
            }
            apps.add(new InstalledApp(name, APPS_FOLDER + id, StringUtils.substringAfterLast("\\" + exe.replace('/', '\\'), "\\"), exe));
        }
        return apps;
    }

    /** The {@code .lnk} files under {@code root}; a folder that cannot be read is left out, the rest still listed. */
    static List<Path> shortcuts(Path root) {
        var collector = new ShortcutCollector();
        try {
            Files.walkFileTree(root, collector);
        } catch (IOException e) {
            log.debug("Unable to list the shortcuts in {}", root, e);
        }
        return collector.shortcuts;
    }

    static final class ShortcutCollector extends SimpleFileVisitor<Path> {
        final List<Path> shortcuts = new ArrayList<>();

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isRegularFile() && StringUtils.endsWithIgnoreCase(file.getFileName().toString(), ".lnk")) {
                shortcuts.add(file);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException e) {
            log.debug("Unable to read {}", file, e);
            return FileVisitResult.CONTINUE;
        }
    }

    private static @Nullable InstalledApp read(Path lnk) {
        try {
            var target = LnkFile.target(Files.readAllBytes(lnk));
            if (target == null) {
                return null;
            }
            var exe = LnkFile.expand(target, System::getenv);
            var app = toApp(lnk, exe);
            return app != null && Files.isRegularFile(Path.of(exe)) ? app : null;
        } catch (IOException | RuntimeException e) {
            log.debug("Unable to read shortcut {}", lnk, e);
            return null;
        }
    }

    /** The app {@code lnk} starts when its {@code target} is a program, unless it is an uninstaller. */
    static @Nullable InstalledApp toApp(Path lnk, @Nullable String target) {
        // FilenameUtils reads both separators, so a Windows path gives the same name on every OS (the tests run on all three).
        var name = StringUtils.removeEndIgnoreCase(FilenameUtils.getName(lnk.toString()), ".lnk");
        if (target == null || !StringUtils.endsWithIgnoreCase(target, ".exe") || StringUtils.containsIgnoreCase(name, "uninstall")) {
            return null;
        }
        return new InstalledApp(name, lnk.toString(), StringUtils.substringAfterLast("\\" + target.replace('/', '\\'), "\\"), target);
    }
}

package com.getpcpanel.report;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.report.dto.BugReportRequest;
import com.getpcpanel.report.dto.BugReportResponse;
import com.getpcpanel.rest.PlatformResource;
import com.getpcpanel.util.io.FileUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Assembles the diagnostics bundle that a reporter attaches to their issue. It is written to
 * {@code <data dir>/reports} — beside {@code logs} and {@code profiles.json} — so it is findable again
 * later without repeating the report, and so "send me the bundle from Tuesday" is answerable.
 *
 * <p>Everything except the user's own words is optional and off unless the dialog asked for it. Only
 * the newest {@link #KEEP_REPORTS} bundles are retained, so the folder cannot grow without bound.
 */
@Log4j2
@ApplicationScoped
public class BugReportService {
    static final String REPORTS_DIR = "reports";
    static final int KEEP_REPORTS = 5;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Inject FileUtil fileUtil;
    @Inject SaveService saveService;
    @Inject ProfileRedactor redactor;
    @Inject SystemInfoCollector systemInfo;
    @Inject BugReportUrlBuilder urlBuilder;
    @Inject PlatformResource platform;
    @Inject DeviceHolder devices;
    @Inject ObjectMapper mapper;

    @ConfigProperty(name = "quarkus.log.file.path")
    String logFilePath;

    @ConfigProperty(name = "quarkus.http.access-log.log-directory")
    String accessLogDirectory;

    @ConfigProperty(name = "quarkus.http.access-log.base-file-name")
    String accessLogBaseName;

    public BugReportResponse create(BugReportRequest request) throws IOException {
        var dir = fileUtil.getFile(REPORTS_DIR);
        Files.createDirectories(dir.toPath());

        var fileName = "pcpanel-report-" + LocalDateTime.now().format(STAMP) + ".zip";
        var target = new File(dir, fileName);

        var info = platform.get();
        var deviceLabel = deviceLabel(request.deviceSerial());
        var issueUrl = urlBuilder.build(request, info.version(), info.os(), deviceLabel, fileName);

        try (var out = new ZipOutputStream(Files.newOutputStream(target.toPath()), StandardCharsets.UTF_8)) {
            writeText(out, "report.md", reportMarkdown(request, deviceLabel, info.version(), info.os()));
            if (request.includeSystemInfo()) {
                writeText(out, "system.txt", systemInfo.collect());
            }
            if (request.includeProfile()) {
                writeText(out, "profiles.json", redactor.redactedJson(saveService.get()));
            }
            if (request.includeClientDiagnostics()) {
                writeText(out, "browser.json", clientDiagnostics(request));
            }
            if (request.includeLog()) {
                for (var logFile : logFiles()) {
                    writeFile(out, "logs/" + logFile.getFileName(), logFile);
                }
            }
        }

        prune(dir);
        log.info("Wrote bug report bundle {}", target);
        return new BugReportResponse(fileName, target.getAbsolutePath(), issueUrl);
    }

    /** The application log and the access log, each with its most recent rotation when one exists. */
    private List<Path> logFiles() {
        var candidates = new ArrayList<Path>();
        var appLog = Path.of(logFilePath);
        candidates.add(appLog);
        candidates.add(appLog.resolveSibling(appLog.getFileName() + ".1"));
        var accessLog = Path.of(accessLogDirectory, accessLogBaseName + ".log");
        candidates.add(accessLog);
        candidates.add(accessLog.resolveSibling(accessLog.getFileName() + ".1"));
        return candidates.stream().filter(Files::isReadable).toList();
    }

    private String deviceLabel(String serial) {
        if (StringUtils.isBlank(serial)) {
            return "";
        }
        return devices.getDevice(serial)
                      .map(d -> d.descriptor().displayName() + " (" + serial + ")")
                      .orElse(serial);
    }

    private String reportMarkdown(BugReportRequest request, String deviceLabel, String version, String os) {
        return """
                # PCPanel bug report

                ## What went wrong

                %s

                ## Steps to reproduce

                %s

                ## What was expected

                %s

                ## Reported against

                - Version: %s
                - OS: %s
                - Device: %s
                """.formatted(
                blankAsUnanswered(request.summary()),
                blankAsUnanswered(request.steps()),
                blankAsUnanswered(request.expected()),
                version, os, StringUtils.defaultIfBlank(deviceLabel, "not device-specific"));
    }

    private String clientDiagnostics(BugReportRequest request) throws IOException {
        // Built as a tree rather than a wrapper record: the record would be one more type needing a
        // native-image reflection registration for no gain.
        var payload = mapper.createObjectNode();
        payload.set("console", mapper.valueToTree(request.consoleEntries() == null ? List.of() : request.consoleEntries()));
        payload.set("failedRequests", mapper.valueToTree(request.failedRequests() == null ? List.of() : request.failedRequests()));
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    }

    private static String blankAsUnanswered(String value) {
        return StringUtils.defaultIfBlank(StringUtils.strip(value), "_(not answered)_");
    }

    private static void writeText(ZipOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static void writeFile(ZipOutputStream out, String name, Path source) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        Files.copy(source, (OutputStream) out);
        out.closeEntry();
    }

    /** Keeps the folder bounded: bundles are a diagnostic convenience, not history worth accumulating. */
    private static void prune(File dir) {
        try (var listing = Files.list(dir.toPath())) {
            listing.filter(p -> p.getFileName().toString().startsWith("pcpanel-report-"))
                   .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                   .skip(KEEP_REPORTS)
                   .forEach(BugReportService::delete);
        } catch (IOException e) {
            log.warn("Unable to prune the reports folder", e);
        }
    }

    private static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            log.warn("Unable to delete old report {}", path, e);
        }
    }
}

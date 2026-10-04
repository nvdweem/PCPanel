package com.getpcpanel.alerts.platform.windows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.alerts.NotificationWatch;
import com.getpcpanel.platform.WindowsBuild;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * The toasts in the Windows notification center, read from its own database
 * ({@code %LOCALAPPDATA%\Microsoft\Windows\Notifications\wpndatabase.db}) with the SQLite built into Windows. Works
 * for any app, unlike the notification listener API, which is for Store apps. Each app is named by its handler id
 * ({@code com.squirrel.Discord.Discord}, {@code MSTeams_8wekyb3d8bbwe!MSTeams}), lower-cased.
 *
 * <p>The database is opened read-only for each look and closed again. Windows keeps it in WAL mode, where a reader
 * never blocks the writer; a look that finds it locked or unreadable keeps the previous answer rather than reporting
 * every notification gone.
 */
@Log4j2
@WindowsBuild
@ApplicationScoped
class WindowsNotificationWatch implements NotificationWatch {
    private static final String QUERY = "SELECT h.PrimaryId, MAX(n.ArrivalTime) FROM Notification n"
            + " JOIN NotificationHandler h ON n.HandlerId = h.RecordId WHERE n.Type = 'toast' GROUP BY h.PrimaryId";
    private static final int BUSY_TIMEOUT_MS = 20;

    private Map<String, Long> last = Map.of();
    private final Set<String> seen = new LinkedHashSet<>();
    private boolean warned;

    @Override
    public synchronized Set<String> appsWithNotifications() {
        return newestNotifications().keySet();
    }

    @Override
    public synchronized Map<String, Long> newestNotifications() {
        database().flatMap(db -> read(db, QUERY)).ifPresent(read -> {
            last = read;
            seen.addAll(read.keySet());
        });
        return last;
    }

    /**
     * The apps seen with toasts in the notification center since the app started. Windows registers hundreds of
     * handlers (one per website that may notify, system components), so only those that showed something are listed.
     */
    @Override
    public synchronized Set<String> sources() {
        newestNotifications();
        return Set.copyOf(seen);
    }

    private Optional<Path> database() {
        var base = System.getenv("LOCALAPPDATA");
        var path = base == null ? null : Path.of(base, "Microsoft", "Windows", "Notifications", "wpndatabase.db");
        if (path == null || !Files.isRegularFile(path)) {
            if (!warned) {
                warned = true;
                log.info("No Windows notification database at {}; notification lights for app notifications stay off", path);
            }
            return Optional.empty();
        }
        return Optional.of(path);
    }

    /**
     * The rows of {@code sql}, a handler id and a number each, by lower-case handler id (the highest number where two
     * meet); empty when the database could not be read.
     */
    static Optional<Map<String, Long>> read(Path database, String sql) {
        WinSqlite3 sqlite;
        try {
            sqlite = WinSqlite3.INSTANCE;
        } catch (Throwable e) {
            log.debug("winsqlite3.dll is not available: {}", e.toString());
            return Optional.empty();
        }
        var dbRef = new PointerByReference();
        var rc = sqlite.sqlite3_open_v2(utf8(database.toString()), dbRef, WinSqlite3.SQLITE_OPEN_READONLY, null);
        var db = dbRef.getValue();
        try {
            if (rc != WinSqlite3.SQLITE_OK || db == null) {
                log.debug("Unable to open {} (SQLite {})", database, rc);
                return Optional.empty();
            }
            sqlite.sqlite3_busy_timeout(db, BUSY_TIMEOUT_MS);
            return query(sqlite, db, sql);
        } finally {
            if (db != null) {
                sqlite.sqlite3_close(db);
            }
        }
    }

    private static Optional<Map<String, Long>> query(WinSqlite3 sqlite, Pointer db, String sql) {
        var stmtRef = new PointerByReference();
        var rc = sqlite.sqlite3_prepare_v2(db, utf8(sql), -1, stmtRef, null);
        var stmt = stmtRef.getValue();
        try {
            if (rc != WinSqlite3.SQLITE_OK || stmt == null) {
                log.debug("Unable to read the notification database (SQLite {})", rc);
                return Optional.empty();
            }
            var result = new HashMap<String, Long>();
            while ((rc = sqlite.sqlite3_step(stmt)) == WinSqlite3.SQLITE_ROW) {
                var handler = text(sqlite.sqlite3_column_text(stmt, 0));
                if (StringUtils.isNotBlank(handler)) {
                    result.merge(handler.toLowerCase(Locale.ROOT), sqlite.sqlite3_column_int64(stmt, 1), Math::max);
                }
            }
            if (rc != WinSqlite3.SQLITE_DONE) {
                log.debug("Reading the notification database stopped (SQLite {})", rc);
                return Optional.empty();
            }
            return Optional.of(result);
        } finally {
            if (stmt != null) {
                sqlite.sqlite3_finalize(stmt);
            }
        }
    }

    @Nullable
    private static String text(@Nullable Pointer utf8) {
        return utf8 == null ? null : utf8.getString(0, StandardCharsets.UTF_8.name());
    }

    private static byte[] utf8(String text) {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        var result = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, result, 0, bytes.length);
        return result;
    }
}

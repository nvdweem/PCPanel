package com.getpcpanel.alerts.platform.windows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

/**
 * JNA binding to the SQLite that ships with Windows 10 and later ({@code winsqlite3.dll}), just what
 * {@link WindowsNotificationWatch} needs to run one query. Text goes in and comes out as UTF-8 bytes, which is what
 * SQLite takes, whatever the system code page. No callbacks.
 *
 * <p>Must be {@code --initialize-at-run-time} in the native image (it calls {@code Native.load}) and registered for
 * reflection (see {@code JnaWin32ReflectionConfig}) plus proxy-config.json.
 */
public interface WinSqlite3 extends StdCallLibrary {
    WinSqlite3 INSTANCE = Native.load("winsqlite3", WinSqlite3.class);

    int SQLITE_OK = 0;
    int SQLITE_ROW = 100;
    int SQLITE_DONE = 101;
    int SQLITE_OPEN_READONLY = 0x1;

    /** {@code filename} is NUL-terminated UTF-8; {@code ppDb} gets a handle to close even when the open failed. */
    int sqlite3_open_v2(byte[] filename, PointerByReference ppDb, int flags, Pointer zVfs);

    /** How long a statement waits for a lock before it gives up with {@code SQLITE_BUSY}. */
    int sqlite3_busy_timeout(Pointer db, int ms);

    int sqlite3_prepare_v2(Pointer db, byte[] sql, int nByte, PointerByReference ppStmt, Pointer pzTail);

    int sqlite3_step(Pointer stmt);

    /** UTF-8 text owned by the statement, valid until the next step; null for NULL. */
    Pointer sqlite3_column_text(Pointer stmt, int column);

    long sqlite3_column_int64(Pointer stmt, int column);

    int sqlite3_finalize(Pointer stmt);

    int sqlite3_close(Pointer db);
}

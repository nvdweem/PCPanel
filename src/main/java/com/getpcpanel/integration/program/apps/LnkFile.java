package com.getpcpanel.integration.program.apps;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * Reads the target path of a Windows shortcut ({@code .lnk}, MS-SHLLINK) in plain Java: the LinkInfo's local base path
 * (Unicode when present) plus its common path suffix, else the environment-variable target of an
 * EnvironmentVariableDataBlock (still holding its {@code %VAR%}s, see {@link #expand}), else the file-system path the
 * target ID list spells out (a drive followed by files and folders, by their long names).
 */
public final class LnkFile {
    private static final int HEADER_SIZE = 0x4C;
    private static final int HAS_LINK_TARGET_ID_LIST = 0x1;
    private static final int HAS_LINK_INFO = 0x2;
    private static final int IS_UNICODE = 0x80;
    /** The five optional StringData entries, in file order: name, relative path, working dir, arguments, icon. */
    private static final int[] STRING_DATA_FLAGS = { 0x4, 0x8, 0x10, 0x20, 0x40 };
    private static final int VOLUME_ID_AND_LOCAL_BASE_PATH = 0x1;
    private static final int ENVIRONMENT_VARIABLE_BLOCK = 0xA0000001;
    /** The shell item extension block that holds a file entry's long name. */
    private static final int FILE_ENTRY_EXTENSION = 0xBEEF0004;
    private static final Pattern VARIABLE = Pattern.compile("%([^%]+)%");

    private LnkFile() {
    }

    /** The path {@code data} points at, or null when it is not a shortcut or names no file path. */
    public static @Nullable String target(byte[] data) {
        try {
            var b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            if (b.getInt(0) != HEADER_SIZE) {
                return null;
            }
            var flags = b.getInt(0x14);
            b.position(HEADER_SIZE);
            String idListPath = null;
            if ((flags & HAS_LINK_TARGET_ID_LIST) != 0) {
                var size = Short.toUnsignedInt(b.getShort());
                idListPath = idListPath(b, b.position(), b.position() + size);
                b.position(b.position() + size);
            }
            String local = null;
            if ((flags & HAS_LINK_INFO) != 0) {
                var start = b.position();
                local = localPath(b, start);
                b.position(start + b.getInt(start));
            }
            if (local != null) {
                return local;
            }
            var charWidth = (flags & IS_UNICODE) != 0 ? 2 : 1;
            for (var flag : STRING_DATA_FLAGS) {
                if ((flags & flag) != 0) {
                    b.position(b.position() + 2 + Short.toUnsignedInt(b.getShort()) * charWidth);
                }
            }
            return StringUtils.defaultIfEmpty(environmentTarget(b), idListPath);
        } catch (BufferUnderflowException | IndexOutOfBoundsException | IllegalArgumentException e) {
            return null;
        }
    }

    /** {@code path} with each {@code %NAME%} replaced by {@code env}'s value; unknown names stay as they are. */
    public static String expand(String path, UnaryOperator<String> env) {
        return VARIABLE.matcher(path).replaceAll(m -> Matcher.quoteReplacement(StringUtils.defaultString(env.apply(m.group(1)), m.group())));
    }

    private static @Nullable String localPath(ByteBuffer b, int start) {
        var headerSize = b.getInt(start + 4);
        var linkInfoFlags = b.getInt(start + 8);
        if ((linkInfoFlags & VOLUME_ID_AND_LOCAL_BASE_PATH) == 0) {
            return null;
        }
        if (headerSize >= 0x24) {
            var unicode = wideString(b, start + b.getInt(start + 0x1C)) + wideString(b, start + b.getInt(start + 0x20));
            if (!unicode.isEmpty()) {
                return unicode;
            }
        }
        var ansi = ansiString(b, start + b.getInt(start + 0x10), Integer.MAX_VALUE) + ansiString(b, start + b.getInt(start + 0x18), Integer.MAX_VALUE);
        return ansi.isEmpty() ? null : ansi;
    }

    /** The path of an ID list of a drive followed by file entries, else null. */
    private static @Nullable String idListPath(ByteBuffer b, int start, int end) {
        String path = null;
        var at = start;
        while (at + 2 <= end) {
            var size = Short.toUnsignedInt(b.getShort(at));
            if (size == 0) {
                break;
            }
            var type = b.get(at + 2) & 0xFF;
            if (path == null && type == 0x1F) {
                at += size; // The root folder (This PC).
                continue;
            }
            if (path == null && (type & 0x70) == 0x20) {
                path = ansiString(b, at + 3, size - 3);
            } else if (path != null && (type & 0x70) == 0x30) {
                path = (path.endsWith("\\") ? path : path + "\\") + fileEntryName(b, at, size, type);
            } else {
                return null;
            }
            at += size;
        }
        return path;
    }

    /** A file entry's long name from its extension block, else its primary name. */
    private static String fileEntryName(ByteBuffer b, int at, int size, int type) {
        var primary = (type & 0x04) != 0 ? wideString(b, at + 14) : ansiString(b, at + 14, size - 14);
        for (var block = at + 14; block + 8 <= at + size; block += 2) {
            if (b.getInt(block + 4) == FILE_ENTRY_EXTENSION) {
                var version = Short.toUnsignedInt(b.getShort(block + 2));
                var name = 18 + (version >= 7 ? 18 : 0) + (version >= 3 ? 2 : 0) + (version >= 9 ? 4 : 0) + (version >= 8 ? 4 : 0);
                var longName = version >= 3 && block + name < at + size ? wideString(b, block + name) : "";
                return longName.isEmpty() ? primary : longName;
            }
        }
        return primary;
    }

    private static @Nullable String environmentTarget(ByteBuffer b) {
        while (b.remaining() >= 8) {
            var at = b.position();
            var size = b.getInt(at);
            if (size < 8) {
                return null;
            }
            if (b.getInt(at + 4) == ENVIRONMENT_VARIABLE_BLOCK && size >= 0x314) {
                var unicode = wideString(b, at + 8 + 260);
                return StringUtils.defaultIfEmpty(unicode, StringUtils.trimToNull(ansiString(b, at + 8, 260)));
            }
            b.position(at + size);
        }
        return null;
    }

    private static String ansiString(ByteBuffer b, int at, int max) {
        var end = at;
        while (end - at < max && b.get(end) != 0) {
            end++;
        }
        var bytes = new byte[end - at];
        b.get(at, bytes);
        return new String(bytes, ansiCharset());
    }

    private static String wideString(ByteBuffer b, int at) {
        var end = at;
        while (b.getShort(end) != 0) {
            end += 2;
        }
        var bytes = new byte[end - at];
        b.get(at, bytes);
        return new String(bytes, StandardCharsets.UTF_16LE);
    }

    private static Charset ansiCharset() {
        try {
            return Charset.forName(System.getProperty("native.encoding", "windows-1252"));
        } catch (IllegalArgumentException e) {
            return StandardCharsets.ISO_8859_1;
        }
    }
}

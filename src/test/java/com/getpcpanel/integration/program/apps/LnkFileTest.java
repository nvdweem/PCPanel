package com.getpcpanel.integration.program.apps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LnkFileTest {
    private static final int HAS_ID_LIST = 0x1;
    private static final int HAS_LINK_INFO = 0x2;
    private static final int HAS_NAME = 0x4;
    private static final int IS_UNICODE = 0x80;
    private static final int HAS_EXP_STRING = 0x200;

    @Test
    void readsTheLocalBasePath() {
        var lnk = lnk(HAS_LINK_INFO, null, ansiLinkInfo("C:\\Program Files\\App\\app.exe", ""), null);

        assertEquals("C:\\Program Files\\App\\app.exe", LnkFile.target(lnk));
    }

    @Test
    void skipsTheIdListBeforeTheLinkInfo() {
        var lnk = lnk(HAS_ID_LIST | HAS_LINK_INFO, new byte[37], ansiLinkInfo("C:\\Tools\\tool.exe", ""), null);

        assertEquals("C:\\Tools\\tool.exe", LnkFile.target(lnk));
    }

    @Test
    void appendsTheCommonPathSuffix() {
        var lnk = lnk(HAS_LINK_INFO, null, ansiLinkInfo("C:\\Games\\", "Game\\game.exe"), null);

        assertEquals("C:\\Games\\Game\\game.exe", LnkFile.target(lnk));
    }

    @Test
    void prefersTheUnicodePath() {
        var lnk = lnk(HAS_LINK_INFO | IS_UNICODE, null, unicodeLinkInfo("C:\\Programme\\Grüße\\app.exe"), null);

        assertEquals("C:\\Programme\\Grüße\\app.exe", LnkFile.target(lnk));
    }

    @Test
    void fallsBackToTheEnvironmentVariableTarget() {
        var lnk = lnk(HAS_ID_LIST | HAS_NAME | IS_UNICODE | HAS_EXP_STRING, new byte[12], null,
                envBlock("%ProgramFiles%\\App\\app.exe"), "A comment");

        assertEquals("%ProgramFiles%\\App\\app.exe", LnkFile.target(lnk));
    }

    @Test
    void expandsEnvironmentVariables() {
        var env = Map.of("ProgramFiles", "C:\\Program Files");

        assertEquals("C:\\Program Files\\App\\app.exe", LnkFile.expand("%ProgramFiles%\\App\\app.exe", env::get));
        assertEquals("%Unknown%\\app.exe", LnkFile.expand("%Unknown%\\app.exe", env::get));
    }

    @Test
    void readsTheLongNamesOfTheIdList() {
        var idList = idList(root(), volume("C:\\"), fileItem(0x31, "PROGRA~2", "Program Files (x86)"), fileItem(0x32, "BATTLE~1.EXE", "Battle.net Launcher.exe"));

        assertEquals("C:\\Program Files (x86)\\Battle.net Launcher.exe", LnkFile.target(lnk(HAS_ID_LIST | IS_UNICODE, idList, null, null)));
    }

    @Test
    void anIdListItemWithoutALongNameUsesItsPrimaryName() {
        var idList = idList(root(), volume("D:\\"), fileItem(0x31, "Tools", null), fileItem(0x32, "tool.exe", null));

        assertEquals("D:\\Tools\\tool.exe", LnkFile.target(lnk(HAS_ID_LIST, idList, null, null)));
    }

    @Test
    void anIdListOutsideTheFileSystemHasNoTarget() {
        var controlPanel = idList(root(), new byte[] { 0x0C, 0, 0x71, 0, 1, 2, 3, 4, 5, 6, 7, 8 });

        assertNull(LnkFile.target(lnk(HAS_ID_LIST, controlPanel, null, null)));
    }

    @Test
    void anUnresolvableShortcutHasNoTarget() {
        assertNull(LnkFile.target(lnk(HAS_ID_LIST, new byte[20], null, null)));
    }

    @Test
    void garbageHasNoTarget() {
        assertNull(LnkFile.target(new byte[0]));
        assertNull(LnkFile.target("not a shortcut".getBytes(StandardCharsets.US_ASCII)));
        var lnk = lnk(HAS_LINK_INFO, null, ansiLinkInfo("C:\\Tools\\tool.exe", ""), null);
        assertNull(LnkFile.target(Arrays.copyOf(lnk, 0x50)));
    }

    // ── fixture builders (MS-SHLLINK layout) ────────────────────────────────────

    private static byte[] lnk(int flags, byte[] idList, byte[] linkInfo, byte[] extra, String... names) {
        var out = new ByteArrayOutputStream();
        var header = le(0x4C);
        header.putInt(0x4C);
        header.put(new byte[] { 0x01, 0x14, 0x02, 0, 0, 0, 0, 0, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 0x46 });
        header.putInt(flags);
        out.writeBytes(header.array());
        if (idList != null) {
            out.writeBytes(le(2).putShort((short) idList.length).array());
            out.writeBytes(idList);
        }
        if (linkInfo != null) {
            out.writeBytes(linkInfo);
        }
        for (var name : names) {
            out.writeBytes(le(2).putShort((short) name.length()).array());
            out.writeBytes(name.getBytes(StandardCharsets.UTF_16LE));
        }
        if (extra != null) {
            out.writeBytes(extra);
        }
        out.writeBytes(le(4).putInt(0).array());
        return out.toByteArray();
    }

    private static byte[] idList(byte[]... items) {
        var out = new ByteArrayOutputStream();
        for (var item : items) {
            out.writeBytes(item);
        }
        out.writeBytes(new byte[2]);
        return out.toByteArray();
    }

    private static byte[] root() {
        var b = le(0x14).putShort((short) 0x14).put((byte) 0x1F).put((byte) 0x50);
        b.put(new byte[] { (byte) 0xE0, 0x4F, (byte) 0xD0, 0x20, (byte) 0xEA, 0x3A, 0x69, 0x10, (byte) 0xA2, (byte) 0xD8, 0x08, 0x00, 0x2B, 0x30, 0x30, (byte) 0x9D });
        return b.array();
    }

    private static byte[] volume(String drive) {
        var b = le(0x19).putShort((short) 0x19).put((byte) 0x2F);
        b.put(drive.getBytes(StandardCharsets.US_ASCII));
        return b.array();
    }

    /** A file entry shell item: header, primary (ANSI) name, then a version 9 BEEF0004 extension block holding the long name. */
    private static byte[] fileItem(int type, String primary, String longName) {
        var body = new ByteArrayOutputStream();
        body.writeBytes(new byte[] { (byte) type, 0 });
        body.writeBytes(new byte[10]);
        var name = cString(primary.getBytes(StandardCharsets.ISO_8859_1));
        body.writeBytes(name);
        if (name.length % 2 == 1) {
            body.write(0);
        }
        if (longName != null) {
            var wide = wString(longName);
            var blockSize = 46 + wide.length + 2;
            var block = le(blockSize);
            block.putShort((short) blockSize).putShort((short) 9).putInt(0xBEEF0004);
            block.position(16).putShort((short) 0x2E);
            block.position(46).put(wide);
            body.writeBytes(block.array());
        }
        var bytes = body.toByteArray();
        return ByteBuffer.allocate(bytes.length + 2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) (bytes.length + 2)).put(bytes).array();
    }

    private static byte[] ansiLinkInfo(String basePath, String suffix) {
        var volumeId = volumeId();
        var base = cString(basePath.getBytes(StandardCharsets.ISO_8859_1));
        var suffixBytes = cString(suffix.getBytes(StandardCharsets.ISO_8859_1));
        var headerSize = 0x1C;
        var size = headerSize + volumeId.length + base.length + suffixBytes.length;
        var b = le(size);
        b.putInt(size).putInt(headerSize).putInt(1)
         .putInt(headerSize)
         .putInt(headerSize + volumeId.length)
         .putInt(0)
         .putInt(headerSize + volumeId.length + base.length);
        b.put(volumeId).put(base).put(suffixBytes);
        return b.array();
    }

    private static byte[] unicodeLinkInfo(String basePath) {
        var volumeId = volumeId();
        var ansi = cString("C:\\PROGRA~1\\GRE~1\\app.exe".getBytes(StandardCharsets.ISO_8859_1));
        var ansiSuffix = cString(new byte[0]);
        var unicode = wString(basePath);
        var unicodeSuffix = wString("");
        var headerSize = 0x24;
        var size = headerSize + volumeId.length + ansi.length + ansiSuffix.length + unicode.length + unicodeSuffix.length;
        var b = le(size);
        var at = headerSize;
        b.putInt(size).putInt(headerSize).putInt(1)
         .putInt(at)
         .putInt(at += volumeId.length)
         .putInt(0)
         .putInt(at += ansi.length)
         .putInt(at += ansiSuffix.length)
         .putInt(at + unicode.length);
        b.put(volumeId).put(ansi).put(ansiSuffix).put(unicode).put(unicodeSuffix);
        return b.array();
    }

    private static byte[] envBlock(String target) {
        var b = le(0x314);
        b.putInt(0x314).putInt(0xA0000001);
        var ansi = Arrays.copyOf(target.getBytes(StandardCharsets.ISO_8859_1), 260);
        var unicode = Arrays.copyOf(target.getBytes(StandardCharsets.UTF_16LE), 520);
        b.put(ansi).put(unicode);
        return b.array();
    }

    private static byte[] volumeId() {
        return le(0x11).putInt(0x11).putInt(3).putInt(0x1234).putInt(0x10).put((byte) 0).array();
    }

    private static byte[] cString(byte[] bytes) {
        return Arrays.copyOf(bytes, bytes.length + 1);
    }

    private static byte[] wString(String s) {
        var bytes = s.getBytes(StandardCharsets.UTF_16LE);
        return Arrays.copyOf(bytes, bytes.length + 2);
    }

    private static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }
}

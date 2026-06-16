package com.getpcpanel.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.zip.InflaterInputStream;

import lombok.extern.log4j.Log4j2;

/**
 * Minimal, dependency-free PNG decoder that produces an ARGB {@link BufferedImage} without touching
 * {@code javax.imageio.ImageIO} or the AWT {@code Toolkit}.
 *
 * <p>ImageIO's static initialization reaches {@code java.awt.Toolkit} (which loads {@code awt.dll}).
 * That native toolkit is unavailable / unreliable in the Windows GraalVM native image and throws
 * {@code NoClassDefFoundError: Could not initialize class java.awt.Toolkit}, which previously crashed
 * the input-handling thread when an overlay icon was loaded. Building a {@code TYPE_INT_ARGB}
 * {@link BufferedImage} via {@code setRGB} is pure Java and behaves identically on the JVM and on the
 * Windows/Linux/macOS native images (it is the same approach the Win32 icon extractor already uses).
 *
 * <p>Supports the case used by the bundled icons: 8-bit, non-interlaced, colour types 0/2/3/4/6
 * (grayscale, RGB, palette, grayscale+alpha, RGBA) with optional {@code tRNS} transparency. Returns
 * {@code null} for anything it does not understand (16-bit, interlaced, non-PNG) so callers can fall
 * back to a blank image.
 */
@Log4j2
public final class PngDecoder {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    // Chunk type tags (big-endian ASCII read as an int).
    private static final int IHDR = 0x49484452;
    private static final int PLTE = 0x504C5445;
    private static final int TRNS = 0x74524E53;
    private static final int IDAT = 0x49444154;
    private static final int IEND = 0x49454E44;

    private PngDecoder() {
    }

    /** Decodes a PNG byte array to an ARGB image, or returns {@code null} if it is not a supported PNG. */
    public static BufferedImage decode(byte[] data) {
        if (data == null || data.length < SIGNATURE.length) {
            return null;
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (data[i] != SIGNATURE[i]) {
                return null;
            }
        }
        try {
            return doDecode(data);
        } catch (IOException | RuntimeException e) {
            log.trace("Unable to decode PNG", e);
            return null;
        }
    }

    private static BufferedImage doDecode(byte[] data) throws IOException {
        int width = 0;
        int height = 0;
        int bitDepth = 0;
        int colorType = -1;
        int interlace = 0;
        byte[] palette = null;       // RGB triplets
        byte[] paletteAlpha = null;  // per-palette-index alpha (tRNS)
        var idat = new ByteArrayOutputStream();

        var in = new DataInputStream(new ByteArrayInputStream(data, SIGNATURE.length, data.length - SIGNATURE.length));
        var end = false;
        while (!end) {
            int len;
            try {
                len = in.readInt();
            } catch (EOFException eof) {
                break;
            }
            var type = in.readInt();
            switch (type) {
                case IHDR -> {
                    width = in.readInt();
                    height = in.readInt();
                    bitDepth = in.readUnsignedByte();
                    colorType = in.readUnsignedByte();
                    in.readUnsignedByte(); // compression method
                    in.readUnsignedByte(); // filter method
                    interlace = in.readUnsignedByte();
                }
                case PLTE -> {
                    palette = new byte[len];
                    in.readFully(palette);
                }
                case TRNS -> {
                    paletteAlpha = new byte[len];
                    in.readFully(paletteAlpha);
                }
                case IDAT -> {
                    var chunk = new byte[len];
                    in.readFully(chunk);
                    idat.write(chunk);
                }
                case IEND -> end = true;
                default -> in.skipBytes(len);
            }
            in.skipBytes(4); // CRC
        }

        if (bitDepth != 8 || interlace != 0 || width <= 0 || height <= 0) {
            return null; // unsupported variant
        }
        var channels = switch (colorType) {
            case 0 -> 1; // grayscale
            case 2 -> 3; // RGB
            case 3 -> 1; // palette index
            case 4 -> 2; // grayscale + alpha
            case 6 -> 4; // RGBA
            default -> -1;
        };
        if (channels < 0) {
            return null;
        }

        var raw = inflate(idat.toByteArray());
        var stride = width * channels;
        if (raw.length < (long) (stride + 1) * height) {
            return null; // truncated
        }

        var cur = new byte[stride];
        var prev = new byte[stride];
        var argb = new int[width * height];
        var pos = 0;
        for (var y = 0; y < height; y++) {
            var filter = raw[pos++] & 0xFF;
            System.arraycopy(raw, pos, cur, 0, stride);
            pos += stride;
            unfilter(filter, cur, prev, channels);
            rowToArgb(cur, argb, y * width, width, colorType, channels, palette, paletteAlpha);
            var tmp = prev;
            prev = cur;
            cur = tmp;
        }

        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        return image;
    }

    private static byte[] inflate(byte[] compressed) throws IOException {
        try (var inf = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            return inf.readAllBytes();
        }
    }

    private static void unfilter(int filter, byte[] cur, byte[] prev, int bpp) {
        switch (filter) {
            case 1 -> { // Sub
                for (var i = bpp; i < cur.length; i++) {
                    cur[i] = (byte) (cur[i] + cur[i - bpp]);
                }
            }
            case 2 -> { // Up
                for (var i = 0; i < cur.length; i++) {
                    cur[i] = (byte) (cur[i] + prev[i]);
                }
            }
            case 3 -> { // Average
                for (var i = 0; i < cur.length; i++) {
                    var a = i >= bpp ? (cur[i - bpp] & 0xFF) : 0;
                    var b = prev[i] & 0xFF;
                    cur[i] = (byte) (cur[i] + ((a + b) >> 1));
                }
            }
            case 4 -> { // Paeth
                for (var i = 0; i < cur.length; i++) {
                    var a = i >= bpp ? (cur[i - bpp] & 0xFF) : 0;
                    var b = prev[i] & 0xFF;
                    var c = i >= bpp ? (prev[i - bpp] & 0xFF) : 0;
                    cur[i] = (byte) (cur[i] + paeth(a, b, c));
                }
            }
            default -> { /* 0 = None, or unknown -> leave as-is */ }
        }
    }

    private static int paeth(int a, int b, int c) {
        var p = a + b - c;
        var pa = Math.abs(p - a);
        var pb = Math.abs(p - b);
        var pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) {
            return a;
        }
        return pb <= pc ? b : c;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void rowToArgb(byte[] row, int[] argb, int outOff, int width, int colorType, int channels, byte[] palette, byte[] paletteAlpha) {
        for (var x = 0; x < width; x++) {
            var s = x * channels;
            int r;
            int g;
            int b;
            int a;
            switch (colorType) {
                case 2 -> {
                    r = row[s] & 0xFF;
                    g = row[s + 1] & 0xFF;
                    b = row[s + 2] & 0xFF;
                    a = 0xFF;
                }
                case 3 -> {
                    var idx = row[s] & 0xFF;
                    var p = idx * 3;
                    if (palette != null && p + 2 < palette.length) {
                        r = palette[p] & 0xFF;
                        g = palette[p + 1] & 0xFF;
                        b = palette[p + 2] & 0xFF;
                    } else {
                        r = g = b = 0;
                    }
                    a = paletteAlpha != null && idx < paletteAlpha.length ? (paletteAlpha[idx] & 0xFF) : 0xFF;
                }
                case 4 -> {
                    r = g = b = row[s] & 0xFF;
                    a = row[s + 1] & 0xFF;
                }
                case 6 -> {
                    r = row[s] & 0xFF;
                    g = row[s + 1] & 0xFF;
                    b = row[s + 2] & 0xFF;
                    a = row[s + 3] & 0xFF;
                }
                default -> { // 0 = grayscale
                    r = g = b = row[s] & 0xFF;
                    a = 0xFF;
                }
            }
            argb[outOff + x] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}

package com.getpcpanel.util.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import lombok.extern.log4j.Log4j2;

/**
 * Minimal, dependency-free PNG encoder that turns a {@link BufferedImage} into PNG bytes without
 * touching {@code javax.imageio.ImageIO} or the AWT {@code Toolkit}.
 *
 * <p>{@code ImageIO.write} triggers the same trap as {@code ImageIO.read}: its static initialization
 * reaches {@code java.awt.Toolkit.loadLibraries} → {@code System.loadLibrary("awt")}, which throws
 * {@code UnsatisfiedLinkError} in the GraalVM native image (even on Windows, which bundles only
 * headless Java2D, not the full AWT windowing toolkit). Reading pixels via {@code getRGB} and
 * deflating them by hand is pure Java and behaves identically on the JVM and on every native image —
 * the counterpart to {@link PngDecoder}.
 *
 * <p>Always emits a non-interlaced, 8-bit, colour-type-6 (RGBA) image with the {@code None} row
 * filter, which {@link PngDecoder} (and every PNG reader) understands.
 */
@Log4j2
public final class PngEncoder {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int IHDR = 0x49484452;
    private static final int IDAT = 0x49444154;
    private static final int IEND = 0x49454E44;

    private PngEncoder() {
    }

    /** Encodes an image to PNG bytes, or returns {@code null} if it cannot be encoded. */
    public static byte[] encode(BufferedImage image) {
        if (image == null) {
            return null;
        }
        var width = image.getWidth();
        var height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        try {
            return doEncode(image, width, height);
        } catch (RuntimeException e) {
            log.trace("Unable to encode PNG", e);
            return null;
        }
    }

    private static byte[] doEncode(BufferedImage image, int width, int height) {
        // Raw, unfiltered scanlines: each row is a 0x00 filter byte followed by RGBA pixels.
        var raw = new ByteArrayOutputStream(height * (1 + width * 4));
        var row = new int[width];
        for (var y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width); // returns 0xAARRGGBB regardless of image type
            raw.write(0); // filter: None
            for (var x = 0; x < width; x++) {
                var argb = row[x];
                raw.write((argb >> 16) & 0xFF); // R
                raw.write((argb >> 8) & 0xFF);  // G
                raw.write(argb & 0xFF);         // B
                raw.write((argb >>> 24) & 0xFF); // A
            }
        }

        var out = new ByteArrayOutputStream();
        out.writeBytes(SIGNATURE);

        var ihdr = new ByteArrayOutputStream();
        writeInt(ihdr, width);
        writeInt(ihdr, height);
        ihdr.write(8); // bit depth
        ihdr.write(6); // colour type: RGBA
        ihdr.write(0); // compression: deflate
        ihdr.write(0); // filter method: adaptive (per-row filter bytes)
        ihdr.write(0); // interlace: none
        writeChunk(out, IHDR, ihdr.toByteArray());

        writeChunk(out, IDAT, deflate(raw.toByteArray()));
        writeChunk(out, IEND, new byte[0]);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        var deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(data);
        deflater.finish();
        var compressed = new ByteArrayOutputStream(data.length / 2 + 16);
        var buffer = new byte[8192];
        while (!deflater.finished()) {
            var n = deflater.deflate(buffer);
            compressed.write(buffer, 0, n);
        }
        deflater.end();
        return compressed.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, int type, byte[] data) {
        writeInt(out, data.length);
        var typeBytes = new byte[]{(byte) (type >>> 24), (byte) (type >>> 16), (byte) (type >>> 8), (byte) type};
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        var crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}

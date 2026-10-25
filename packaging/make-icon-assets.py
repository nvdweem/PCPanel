#!/usr/bin/env python3
"""Regenerate the icons derived from app-icon.png: the Linux tray pixmap and the web favicon.

Run from the repository root after changing app-icon.png (needs Pillow: `pip install pillow`):

    python3 packaging/make-icon-assets.py

- src/main/resources/assets/tray-icon.argb: the StatusNotifierItem IconPixmap frames, read by
  WaylandTrayIcon without AWT. Big-endian: int count, then per frame int width, int height and
  width*height*4 bytes of ARGB.
- src/main/webui/public/favicon.ico: the browser tab and app window icon.
"""
import struct
from pathlib import Path

from PIL import Image

SOURCE = Path("app-icon.png")
TRAY = Path("src/main/resources/assets/tray-icon.argb")
FAVICON = Path("src/main/webui/public/favicon.ico")
TRAY_SIZES = (16, 22, 24, 32, 48, 64)
FAVICON_SIZES = (16, 32, 48)


def main():
    icon = Image.open(SOURCE).convert("RGBA")

    out = bytearray(struct.pack(">i", len(TRAY_SIZES)))
    for size in TRAY_SIZES:
        rgba = icon.resize((size, size), Image.LANCZOS).tobytes()
        out += struct.pack(">ii", size, size)
        for i in range(0, len(rgba), 4):
            out += bytes((rgba[i + 3], rgba[i], rgba[i + 1], rgba[i + 2]))
    TRAY.write_bytes(out)

    icon.save(FAVICON, sizes=[(s, s) for s in FAVICON_SIZES])


if __name__ == "__main__":
    main()

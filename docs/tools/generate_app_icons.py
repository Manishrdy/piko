#!/usr/bin/env python3
"""
Turns a square source image into the mipmap resources the "Change app icon" patch
needs for one Instagram launcher icon.

Usage:
    generate_app_icons.py <slug> <source.png> [--mode tile|logo] [--background "#RRGGBB"]

    slug            Identifier for the icon. Becomes the resource name and, with
                    underscores turned into spaces and words capitalised, the label
                    shown in the picker: "retro_glow" reads as "Retro Glow".
    source.png      Square source image. 1024x1024 with transparency is ideal.
    --mode tile     (default) The source is already a finished app icon: a filled
                    square or rounded square, usually with a transparent margin
                    around it. The margin is trimmed, the tile is placed at safe-zone
                    size in the foreground, and a zoomed copy fills the background so
                    the launcher's mask has matching colour to bite into.
    --mode logo     The source is bare artwork on a transparent background. It is
                    inset into the safe zone over a flat --background colour.
    --background    Backdrop colour. Only used by --mode logo, where it defaults to
                    white; tile mode samples the tile's own edge instead.

An adaptive icon layer is 108dp square, of which only the central 72dp is reliably
visible -- launcher masks crop the rest. Both modes keep the artwork inside that
window and make sure nothing transparent can reach the edge.

Writes into patches/src/main/resources/instagram/appicons:

    mipmap-anydpi-v26/piko_icon_<slug>.xml
    mipmap-<density>/piko_icon_<slug>_foreground.webp
    mipmap-<density>/piko_icon_<slug>_background.webp

then prints the line to add to bundledIcons in AppIcons.kt.

Needs Pillow:

    python3 -m venv docs/tools/.venv
    docs/tools/.venv/bin/pip install Pillow
    docs/tools/.venv/bin/python docs/tools/generate_app_icons.py ...
"""

import argparse
import re
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit(
        "Pillow is not installed. Set it up with:\n"
        "  python3 -m venv docs/tools/.venv\n"
        "  docs/tools/.venv/bin/pip install Pillow\n"
        "then re-run this script with docs/tools/.venv/bin/python."
    )

CANVAS_DP = 108
SAFE_ZONE_DP = 72

# Only the two buckets modern phones actually use. Lower-density devices scale down
# from xxhdpi, which costs nothing visible and keeps the patch bundle small -- the
# same trade the Twitter app icon patch makes by shipping xxhdpi alone.
DENSITIES = {
    "xxhdpi": 3,
    "xxxhdpi": 4,
}

RESOURCE_ROOT = Path("patches/src/main/resources/instagram/appicons")

ADAPTIVE_ICON_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/piko_icon_{slug}_background" />
    <foreground android:drawable="@mipmap/piko_icon_{slug}_foreground" />
</adaptive-icon>
"""


def parse_color(value):
    match = re.fullmatch(r"#?([0-9a-fA-F]{6})", value.strip())
    if not match:
        raise argparse.ArgumentTypeError(f"expected a colour like #1E88E5, got {value!r}")
    digits = match.group(1)
    return tuple(int(digits[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def parse_slug(value):
    if not re.fullmatch(r"[a-z][a-z0-9_]*", value):
        raise argparse.ArgumentTypeError(
            f"slug must be lowercase letters, digits and underscores, got {value!r}"
        )
    return value


def trim_transparent_margin(image):
    bbox = image.getchannel("A").getbbox()
    return image.crop(bbox) if bbox else image


def sample_edge_color(image):
    """Average of the opaque pixels around the artwork's border.

    Used to flood the background layer so that the corners a launcher mask keeps --
    which the tile's own rounded corners leave transparent -- still carry the tile's
    colour instead of showing through.
    """
    width, height = image.size
    inset = max(1, min(width, height) // 12)

    total = [0, 0, 0]
    count = 0
    for x in range(0, width, max(1, width // 64)):
        for y in (inset, height - 1 - inset):
            pixel = image.getpixel((x, y))
            if pixel[3] > 128:
                total = [total[i] + pixel[i] for i in range(3)]
                count += 1
    for y in range(0, height, max(1, height // 64)):
        for x in (inset, width - 1 - inset):
            pixel = image.getpixel((x, y))
            if pixel[3] > 128:
                total = [total[i] + pixel[i] for i in range(3)]
                count += 1

    if count == 0:
        return (255, 255, 255, 255)
    return tuple(value // count for value in total) + (255,)


def build_layers(source, canvas_px, mode, background):
    """Returns the (foreground, background) images for one density."""
    artwork_px = round(canvas_px * SAFE_ZONE_DP / CANVAS_DP)
    offset = (canvas_px - artwork_px) // 2

    foreground = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    artwork = source.resize((artwork_px, artwork_px), Image.LANCZOS)
    foreground.paste(artwork, (offset, offset), artwork)

    if mode == "logo":
        return foreground, Image.new("RGBA", (canvas_px, canvas_px), background)

    # Tile mode: a zoomed copy of the tile bleeds to the edges, over its own edge
    # colour so the parts its rounded corners leave transparent are still filled.
    backdrop = Image.new("RGBA", (canvas_px, canvas_px), background)
    bleed = source.resize((canvas_px, canvas_px), Image.LANCZOS)
    backdrop.paste(bleed, (0, 0), bleed)
    return foreground, backdrop


def main():
    parser = argparse.ArgumentParser(
        description="Generate adaptive launcher icon resources for the piko Instagram patch."
    )
    parser.add_argument("slug", type=parse_slug)
    parser.add_argument("source", type=Path)
    parser.add_argument("--mode", choices=("tile", "logo"), default="tile")
    parser.add_argument("--background", type=parse_color, default=None)
    args = parser.parse_args()

    if not RESOURCE_ROOT.parent.parent.exists():
        sys.exit(f"run this from the repository root -- {RESOURCE_ROOT} is not reachable")

    source = Image.open(args.source).convert("RGBA")

    if args.mode == "tile":
        source = trim_transparent_margin(source)
        background = args.background or sample_edge_color(source)
    else:
        background = args.background or parse_color("#FFFFFF")

    if source.width != source.height:
        print(
            f"warning: artwork is {source.width}x{source.height}, not square -- "
            "it will be squashed to fit",
            file=sys.stderr,
        )

    for density, scale in DENSITIES.items():
        canvas_px = round(CANVAS_DP * scale)
        foreground, backdrop = build_layers(source, canvas_px, args.mode, background)

        directory = RESOURCE_ROOT / f"mipmap-{density}"
        directory.mkdir(parents=True, exist_ok=True)
        # Lossless for the foreground so its alpha edge stays clean, lossy for the
        # background, which is fully opaque and only ever shows smooth colour.
        foreground.save(directory / f"piko_icon_{args.slug}_foreground.webp", lossless=True)
        backdrop.convert("RGB").save(
            directory / f"piko_icon_{args.slug}_background.webp", quality=92, method=6
        )

    xml_directory = RESOURCE_ROOT / "mipmap-anydpi-v26"
    xml_directory.mkdir(parents=True, exist_ok=True)
    (xml_directory / f"piko_icon_{args.slug}.xml").write_text(
        ADAPTIVE_ICON_XML.format(slug=args.slug)
    )

    hex_background = "#%02X%02X%02X" % background[:3]
    print(
        f"Wrote {len(DENSITIES) * 2 + 1} files for {args.slug!r} "
        f"({args.mode} mode, background {hex_background}) under {RESOURCE_ROOT}."
    )
    print("Add it to bundledIcons in")
    print("  patches/src/main/kotlin/app/crimera/patches/instagram/fork/appIcon/AppIcons.kt")
    print(f'        "{args.slug}",')


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Build SatGlobe's compact equirectangular Earth texture.

Coastline data: Natural Earth 1:110m land polygons (public domain).
The generated bitmap is deliberately stylized and contains no labels.
"""

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools" / "ne_110m_land.geojson"
OUTPUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "earth_texture.png"
WIDTH = 1024
HEIGHT = 512
SCALE = 2


def xy(longitude: float, latitude: float) -> tuple[float, float]:
    x = (longitude + 180.0) / 360.0 * WIDTH * SCALE
    y = (90.0 - latitude) / 180.0 * HEIGHT * SCALE
    return x, y


def unwrap_ring(ring):
    """Keep consecutive longitudes close so dateline polygons do not span Earth."""
    result = []
    previous = None
    offset = 0.0
    for longitude, latitude in ring:
        candidate = longitude + offset
        if previous is not None:
            while candidate - previous > 180.0:
                candidate -= 360.0
                offset -= 360.0
            while candidate - previous < -180.0:
                candidate += 360.0
                offset += 360.0
        result.append((candidate, latitude))
        previous = candidate
    return result


def polygon_copies(ring):
    unwrapped = unwrap_ring(ring)
    points = [xy(longitude, latitude) for longitude, latitude in unwrapped]
    canvas_width = WIDTH * SCALE
    return [
        [(x + shift * canvas_width, y) for x, y in points]
        for shift in (-1, 0, 1)
    ]


def iter_polygons(geometry):
    if geometry["type"] == "Polygon":
        yield geometry["coordinates"]
    elif geometry["type"] == "MultiPolygon":
        yield from geometry["coordinates"]


def build_ocean():
    image = Image.new("RGB", (WIDTH * SCALE, HEIGHT * SCALE))
    pixels = image.load()
    for y in range(HEIGHT * SCALE):
        latitude_factor = abs((y / (HEIGHT * SCALE - 1)) * 2.0 - 1.0)
        for x in range(WIDTH * SCALE):
            wave = (
                math.sin(x * 0.018 + y * 0.007)
                + math.sin(x * 0.006 - y * 0.013)
            ) * 1.8
            red = int(5 + 4 * (1 - latitude_factor) + wave * 0.25)
            green = int(22 + 12 * (1 - latitude_factor) + wave * 0.7)
            blue = int(39 + 20 * (1 - latitude_factor) + wave)
            pixels[x, y] = (
                max(0, min(255, red)),
                max(0, min(255, green)),
                max(0, min(255, blue)),
            )
    return image


def main():
    data = json.loads(SOURCE.read_text(encoding="utf-8"))
    output_size = (WIDTH * SCALE, HEIGHT * SCALE)
    ocean = build_ocean()
    land_mask = Image.new("L", output_size, 0)
    mask_draw = ImageDraw.Draw(land_mask)

    for feature in data["features"]:
        for polygon in iter_polygons(feature["geometry"]):
            outer, *holes = polygon
            for points in polygon_copies(outer):
                mask_draw.polygon(points, fill=255)
            for hole in holes:
                for points in polygon_copies(hole):
                    mask_draw.polygon(points, fill=0)

    land = Image.new("RGB", output_size)
    land_pixels = land.load()
    for y in range(HEIGHT * SCALE):
        latitude = 90.0 - y / (HEIGHT * SCALE - 1) * 180.0
        polar = max(0.0, (abs(latitude) - 58.0) / 32.0)
        tropical = max(0.0, 1.0 - abs(latitude) / 55.0)
        for x in range(WIDTH * SCALE):
            texture = (
                math.sin(x * 0.031) * 2.0
                + math.sin(y * 0.023 + x * 0.008) * 2.0
            )
            land_pixels[x, y] = (
                int(21 + 40 * polar + 4 * tropical + texture),
                int(92 + 58 * polar + 24 * tropical + texture),
                int(76 + 62 * polar + 6 * tropical + texture * 0.7),
            )

    image = Image.composite(land, ocean, land_mask)
    draw = ImageDraw.Draw(image, "RGBA")

    # Latitude/longitude grid kept subtle so markers remain dominant.
    for longitude in range(-150, 180, 30):
        x, _ = xy(longitude, 0)
        draw.line([(x, 0), (x, HEIGHT * SCALE)], fill=(97, 205, 218, 28), width=SCALE)
    for latitude in range(-60, 90, 30):
        _, y = xy(0, latitude)
        draw.line([(0, y), (WIDTH * SCALE, y)], fill=(97, 205, 218, 26), width=SCALE)

    # Coastline glow and crisp inner line.
    edge = land_mask.filter(ImageFilter.FIND_EDGES)
    glow = edge.filter(ImageFilter.GaussianBlur(radius=2.2 * SCALE))
    glow_color = Image.new("RGBA", output_size, (68, 231, 191, 95))
    image = Image.alpha_composite(image.convert("RGBA"), Image.composite(glow_color, Image.new("RGBA", output_size), glow))
    coast_color = Image.new("RGBA", output_size, (111, 245, 213, 130))
    image = Image.alpha_composite(image, Image.composite(coast_color, Image.new("RGBA", output_size), edge))

    image = image.convert("RGB").resize((WIDTH, HEIGHT), Image.Resampling.LANCZOS)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT, "PNG", optimize=True)
    print(f"Generated {OUTPUT} ({OUTPUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()

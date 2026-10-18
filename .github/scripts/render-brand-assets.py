"""Generate website icons, social image and Android raster fallbacks from one logo."""
from pathlib import Path
import cairosvg

ROOT = Path(__file__).resolve().parents[2]
for folder in ("docs", "site"):
    site = ROOT / folder
    logo = site / "assets" / "logo.svg"
    for filename, size in (
        ("favicon-16.png", 16), ("favicon-32.png", 32),
        ("apple-touch-icon.png", 180), ("icon-192.png", 192),
        ("icon-512.png", 512),
    ):
        cairosvg.svg2png(url=str(logo), write_to=str(site / "assets" / filename),
                        output_width=size, output_height=size)
    cairosvg.svg2png(url=str(site / "assets" / "og-cover.svg"),
                    write_to=str(site / "assets" / "og-cover.png"),
                    output_width=1200, output_height=630)

res = ROOT / "RoutixStandalone" / "app" / "src" / "main" / "res"
logo = ROOT / "docs" / "assets" / "logo.svg"
for density, size in (("mdpi",48),("hdpi",72),("xhdpi",96),
                      ("xxhdpi",144),("xxxhdpi",192)):
    dest = res / ("mipmap-" + density)
    dest.mkdir(parents=True,exist_ok=True)
    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        cairosvg.svg2png(url=str(logo),write_to=str(dest / name),
                        output_width=size,output_height=size)

store = ROOT / "branding" / "play-store-512.png"
store.parent.mkdir(parents=True,exist_ok=True)
cairosvg.svg2png(url=str(logo),write_to=str(store),
                output_width=512,output_height=512)

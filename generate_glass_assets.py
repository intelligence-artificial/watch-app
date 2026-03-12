#!/usr/bin/env python3
"""
WetPet Glassmorphism Asset Generator
Renders frosted-glass complication bubbles, ECG waveforms,
glow ring, and glass pill for the V3 watch face design.

Output: PNGs in the watch_face drawable directory.
"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import numpy as np
import math
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
WATCHFACE_DRAWABLE = os.path.join(
    SCRIPT_DIR,
    "wetpet-watch-app/watch_face/src/main/res/drawable"
)

# ── BPM zone colors ──
ZONES = {
    "blue":  {"ring": (68, 136, 255), "glow": (68, 136, 255, 40)},
    "green": {"ring": (0, 214, 143),  "glow": (0, 214, 143, 40)},
    "amber": {"ring": (255, 184, 0),  "glow": (255, 184, 0, 40)},
    "red":   {"ring": (255, 51, 102), "glow": (255, 51, 102, 40)},
}

STEPS_COLOR = {"ring": (0, 229, 255), "glow": (0, 229, 255, 40)}
BATTERY_COLOR = {"ring": (80, 255, 120), "glow": (80, 255, 120, 40)}


def radial_gradient(size, center_color, edge_color):
    """Create a radial gradient image."""
    w, h = size
    arr = np.zeros((h, w, 4), dtype=np.uint8)
    cx, cy = w / 2, h / 2
    max_r = math.sqrt(cx ** 2 + cy ** 2)

    for y in range(h):
        for x in range(w):
            r = math.sqrt((x - cx) ** 2 + (y - cy) ** 2)
            t = min(r / max_r, 1.0)
            for c in range(4):
                arr[y, x, c] = int(center_color[c] * (1 - t) + edge_color[c] * t)

    return Image.fromarray(arr, "RGBA")


def make_glass_circle(size, tint_color, glow_color, ring_thickness=3):
    """
    Create a frosted glass circle with:
    - Dark semi-transparent fill
    - Subtle radial gradient for depth
    - Thin bright ring edge
    - Outer glow
    """
    # Work at 2x for anti-aliasing
    s2 = size * 2
    img = Image.new("RGBA", (s2, s2), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Outer glow (soft, spread)
    glow_r, glow_g, glow_b, glow_a = glow_color
    for i in range(12, 0, -1):
        alpha = int(glow_a * (1 - i / 12))
        pad = i * 2
        draw.ellipse(
            [pad, pad, s2 - pad - 1, s2 - pad - 1],
            fill=(glow_r, glow_g, glow_b, alpha)
        )

    # Dark fill circle
    pad = 24
    draw.ellipse(
        [pad, pad, s2 - pad - 1, s2 - pad - 1],
        fill=(12, 12, 24, 220)
    )

    # Inner radial gradient for glassmorphism depth
    grad_size = s2 - pad * 2
    grad = radial_gradient(
        (grad_size, grad_size),
        center_color=(255, 255, 255, 12),
        edge_color=(0, 0, 0, 0)
    )
    # Mask to circle
    mask = Image.new("L", (grad_size, grad_size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, grad_size - 1, grad_size - 1], fill=255)
    grad.putalpha(mask)
    img.paste(grad, (pad, pad), grad)

    # Highlight arc at top (frosted glass reflection)
    highlight = Image.new("RGBA", (s2, s2), (0, 0, 0, 0))
    h_draw = ImageDraw.Draw(highlight)
    h_draw.arc(
        [pad + 6, pad + 6, s2 - pad - 7, s2 - pad - 7],
        start=200, end=340,
        fill=(255, 255, 255, 30),
        width=4
    )
    img = Image.alpha_composite(img, highlight)

    # Ring edge
    r, g, b = tint_color
    ring_pad = pad + 2
    ring_w = ring_thickness * 2
    draw2 = ImageDraw.Draw(img)
    draw2.ellipse(
        [ring_pad, ring_pad, s2 - ring_pad - 1, s2 - ring_pad - 1],
        outline=(r, g, b, 100),
        width=ring_w
    )

    # Downscale for AA
    return img.resize((size, size), Image.LANCZOS)


def make_ecg_waveform(size, color, alpha=40):
    """
    Draw a classic PQRST ECG waveform pattern inside a circle mask.
    """
    s2 = size * 2
    img = Image.new("RGBA", (s2, s2), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    r, g, b = color
    cx, cy = s2 // 2, s2 // 2

    # ECG waveform points (normalized 0-1 for x, -1 to 1 for y)
    # Classic PQRST pattern
    ecg_points = [
        (0.00, 0.0), (0.05, 0.0), (0.10, 0.0),
        # P wave
        (0.15, -0.08), (0.20, -0.15), (0.25, -0.08),
        (0.28, 0.0),
        # PR segment
        (0.32, 0.0),
        # Q dip
        (0.35, 0.08),
        # R spike
        (0.38, -0.65), (0.40, -0.85),
        # S dip
        (0.42, 0.30), (0.44, 0.15),
        # ST segment
        (0.48, 0.0), (0.52, 0.0),
        # T wave
        (0.56, -0.10), (0.62, -0.22), (0.68, -0.10),
        (0.72, 0.0),
        # Flat
        (0.80, 0.0), (0.90, 0.0), (1.00, 0.0),
    ]

    # Draw waveform
    margin = s2 * 0.22
    w_width = s2 - margin * 2
    w_height = s2 * 0.35

    points = []
    for px, py in ecg_points:
        x = margin + px * w_width
        y = cy + py * w_height
        points.append((int(x), int(y)))

    for i in range(len(points) - 1):
        draw.line([points[i], points[i + 1]], fill=(r, g, b, alpha), width=3)

    # Circle mask
    mask = Image.new("L", (s2, s2), 0)
    pad = 28
    ImageDraw.Draw(mask).ellipse([pad, pad, s2 - pad - 1, s2 - pad - 1], fill=255)
    img.putalpha(mask)

    return img.resize((size, size), Image.LANCZOS)


def make_glass_pill(width, height, tint_color, glow_color):
    """Create a frosted glass pill/capsule shape for battery."""
    s2w, s2h = width * 2, height * 2
    img = Image.new("RGBA", (s2w, s2h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    r, g, b = tint_color
    gr, gg, gb, ga = glow_color
    corner_r = s2h // 2

    # Outer glow
    for i in range(8, 0, -1):
        alpha = int(ga * (1 - i / 8))
        pad = i * 2
        draw.rounded_rectangle(
            [pad, pad, s2w - pad - 1, s2h - pad - 1],
            radius=corner_r,
            fill=(gr, gg, gb, alpha)
        )

    # Dark fill
    pad = 16
    draw.rounded_rectangle(
        [pad, pad, s2w - pad - 1, s2h - pad - 1],
        radius=corner_r - 8,
        fill=(12, 12, 24, 210)
    )

    # Highlight line at top
    draw.rounded_rectangle(
        [pad + 4, pad + 4, s2w - pad - 5, s2h - pad - 5],
        radius=corner_r - 12,
        outline=(255, 255, 255, 20),
        width=2
    )

    # Subtle ring
    draw.rounded_rectangle(
        [pad + 1, pad + 1, s2w - pad - 2, s2h - pad - 2],
        radius=corner_r - 9,
        outline=(r, g, b, 70),
        width=3
    )

    return img.resize((width, height), Image.LANCZOS)


def make_glow_ring(size, color=(0, 214, 143), ring_radius=None):
    """Create a soft glow ring for the pet area."""
    s2 = size * 2
    img = Image.new("RGBA", (s2, s2), (0, 0, 0, 0))

    if ring_radius is None:
        ring_radius = s2 // 2 - 20

    r, g, b = color
    cx, cy = s2 // 2, s2 // 2

    # Draw concentric rings with decreasing alpha for glow
    arr = np.zeros((s2, s2, 4), dtype=np.uint8)
    for y in range(s2):
        for x in range(s2):
            dist = math.sqrt((x - cx) ** 2 + (y - cy) ** 2)
            ring_dist = abs(dist - ring_radius)
            if ring_dist < 25:
                # Glow falloff
                alpha = int(60 * max(0, 1 - ring_dist / 25))
                arr[y, x] = [r, g, b, alpha]
            # Inner edge highlight
            if ring_dist < 4:
                alpha = int(120 * max(0, 1 - ring_dist / 4))
                arr[y, x] = [r, g, b, alpha]

    result = Image.fromarray(arr, "RGBA")
    return result.resize((size, size), Image.LANCZOS)


def main():
    print("🔮 WetPet Glassmorphism Asset Generator")
    os.makedirs(WATCHFACE_DRAWABLE, exist_ok=True)

    # ── Steps glass bubble ──
    print("\n📐 Steps bubble...")
    bubble = make_glass_circle(76, STEPS_COLOR["ring"], STEPS_COLOR["glow"])
    bubble.save(os.path.join(WATCHFACE_DRAWABLE, "glass_bubble_steps.png"))
    print("  ✓ glass_bubble_steps.png")

    # ── Heart Rate glass bubbles (4 zones) ──
    for zone_name, colors in ZONES.items():
        print(f"\n❤️  HR bubble ({zone_name})...")
        bubble = make_glass_circle(76, colors["ring"], colors["glow"])
        bubble.save(os.path.join(WATCHFACE_DRAWABLE, f"glass_hr_{zone_name}.png"))
        print(f"  ✓ glass_hr_{zone_name}.png")

        # ECG waveform overlay
        ecg = make_ecg_waveform(76, colors["ring"], alpha=35)
        ecg.save(os.path.join(WATCHFACE_DRAWABLE, f"ecg_{zone_name}.png"))
        print(f"  ✓ ecg_{zone_name}.png")

    # ── Battery glass pill ──
    print("\n🔋 Battery pill...")
    pill = make_glass_pill(120, 30, BATTERY_COLOR["ring"], BATTERY_COLOR["glow"])
    pill.save(os.path.join(WATCHFACE_DRAWABLE, "glass_pill_battery.png"))
    print("  ✓ glass_pill_battery.png")

    # ── Pet glow ring ──
    print("\n✨ Pet glow ring...")
    glow = make_glow_ring(110, color=(0, 214, 143))
    glow.save(os.path.join(WATCHFACE_DRAWABLE, "glow_ring_pet.png"))
    print("  ✓ glow_ring_pet.png")

    # Count assets
    glass_files = [f for f in os.listdir(WATCHFACE_DRAWABLE)
                   if f.startswith(("glass_", "ecg_", "glow_"))]
    print(f"\n✅ Generated {len(glass_files)} glassmorphism assets!")
    for f in sorted(glass_files):
        path = os.path.join(WATCHFACE_DRAWABLE, f)
        size_kb = os.path.getsize(path) / 1024
        print(f"   {f} ({size_kb:.1f} KB)")


if __name__ == "__main__":
    main()

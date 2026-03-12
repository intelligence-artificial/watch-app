#!/usr/bin/env python3
"""
WetPet Sprite Processor v4 — Crops generated sprite sheets, colorizes,
and exports all variants for the watch app + watchface.

Input: AI-generated sprite sheet PNGs (blob, cat, dog) with 3 poses each
Output: Individual 144x144 PNGs in all 4 color themes + AOD grayscale

Process:
1. Load sprite sheet
2. Find sprite bounding boxes (non-black regions)
3. Crop each sprite to its content
4. Remove black background → transparent
5. Tint white/gray body into each color theme
6. Export to drawable directories
"""
from PIL import Image, ImageDraw, ImageFilter
import numpy as np
import os
import sys

# ── Output directories ──
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
WEAR_DRAWABLE = os.path.join(SCRIPT_DIR, "wetpet-watch-app/wear/src/main/res/drawable")
WATCHFACE_DRAWABLE = os.path.join(SCRIPT_DIR, "wetpet-watchface/watch_faces/src/main/res/drawable")
ARTIFACTS_DIR = os.path.join(os.path.expanduser("~"), ".gemini/antigravity/brain/1f5ab1b7-99a8-4556-be00-eb98ab0e5c10")

OUT_SIZE = 144  # Final sprite size

# ── Color themes (hue shifts from white/gray base) ──
THEMES = {
    "green":  {"body": (100, 220, 140), "shadow": (55, 150, 85), "highlight": (160, 255, 195)},
    "blue":   {"body": (80, 170, 235),  "shadow": (35, 105, 170), "highlight": (145, 215, 255)},
    "pink":   {"body": (235, 120, 185), "shadow": (175, 65, 130), "highlight": (255, 185, 225)},
    "yellow": {"body": (235, 215, 80),  "shadow": (185, 165, 35), "highlight": (255, 240, 155)},
}

# ── Sprite sheet configs ──
SPRITE_SHEETS = {
    "pet": {
        "file": os.path.join(ARTIFACTS_DIR, "blob_pet_sprites_1773285240372.png"),
        "poses": ["idle_1", "idle_2", "sleep"],
        "grid": "auto",  # auto-detect sprite positions
    },
    "cat": {
        "file": os.path.join(ARTIFACTS_DIR, "cat_pet_sprites_1773285251608.png"),
        "poses": ["idle_1", "idle_2", "sleep"],
        "grid": "auto",
    },
    "dog": {
        "file": os.path.join(ARTIFACTS_DIR, "dog_pet_sprites_1773285269456.png"),
        "poses": ["idle_1", "idle_2", "sleep"],
        "grid": "auto",
    },
}


def find_sprite_regions(img, num_sprites=3, threshold=30):
    """
    Find bounding boxes of sprites in a sprite sheet by detecting
    non-black regions. Returns list of (left, top, right, bottom) tuples.
    """
    arr = np.array(img.convert("RGB"))
    
    # Create mask of non-black pixels
    brightness = arr.max(axis=2)
    mask = brightness > threshold
    
    # Find columns that have content
    col_has_content = mask.any(axis=0)
    row_has_content = mask.any(axis=1)
    
    # Find row bounds (shared by all sprites)
    rows_with_content = np.where(row_has_content)[0]
    if len(rows_with_content) == 0:
        print("  ⚠ No content found!")
        return []
    
    top = rows_with_content[0]
    bottom = rows_with_content[-1]
    
    # Check if sprites are in a 2-row grid (like the blob sheet)
    # Find gaps in rows
    row_gaps = []
    in_content = False
    start = 0
    for i, has in enumerate(row_has_content):
        if has and not in_content:
            start = i
            in_content = True
        elif not has and in_content:
            row_gaps.append((start, i - 1))
            in_content = False
    if in_content:
        row_gaps.append((start, len(row_has_content) - 1))
    
    # If we have 2 rows of sprites, use the first row
    if len(row_gaps) >= 2:
        # Check if the gap between rows is significant
        gap_size = row_gaps[1][0] - row_gaps[0][1]
        if gap_size > 10:
            # Use first row only
            top = row_gaps[0][0]
            bottom = row_gaps[0][1]
    
    # Find column-based sprite boundaries
    cols_with_content = np.where(col_has_content)[0]
    
    # Find gaps between sprites (columns with no content)
    gaps = []
    prev = cols_with_content[0]
    for col in cols_with_content[1:]:
        if col - prev > 5:  # Gap of 5+ pixels = sprite boundary
            gaps.append((prev, col))
        prev = col
    
    # Build sprite regions from gaps
    regions = []
    start_col = cols_with_content[0]
    for gap_end, gap_start in gaps:
        regions.append((start_col, top, gap_end, bottom))
        start_col = gap_start
    regions.append((start_col, top, cols_with_content[-1], bottom))
    
    # If we got more sprites than expected, take the first num_sprites
    if len(regions) > num_sprites:
        regions = regions[:num_sprites]
    
    # If we got fewer, try equal-width division
    if len(regions) < num_sprites:
        w = img.width
        sprite_w = w // num_sprites
        regions = []
        for i in range(num_sprites):
            regions.append((i * sprite_w, top, (i + 1) * sprite_w - 1, bottom))
    
    return regions


def crop_and_clean(img, region, out_size=OUT_SIZE):
    """
    Crop a sprite from the sheet, remove black background,
    center it, and resize to out_size x out_size.
    """
    left, top, right, bottom = region
    cropped = img.crop((left, top, right + 1, bottom + 1))
    
    # Convert to RGBA
    cropped = cropped.convert("RGBA")
    arr = np.array(cropped)
    
    # Remove black/near-black background → transparent
    # Also remove text labels
    rgb = arr[:, :, :3]
    brightness = rgb.max(axis=2)
    
    # Black pixels become transparent
    black_mask = brightness < 35
    arr[black_mask, 3] = 0
    
    result = Image.fromarray(arr)
    
    # Find content bounds and center
    bbox = result.getbbox()
    if bbox:
        content = result.crop(bbox)
        cw, ch = content.size
        
        # Make square, centered
        max_dim = max(cw, ch) + 4  # Small padding
        square = Image.new("RGBA", (max_dim, max_dim), (0, 0, 0, 0))
        paste_x = (max_dim - cw) // 2
        paste_y = (max_dim - ch) // 2
        square.paste(content, (paste_x, paste_y))
        
        # Resize to output size with nearest-neighbor (crisp pixels)
        return square.resize((out_size, out_size), Image.NEAREST)
    
    return result.resize((out_size, out_size), Image.NEAREST)


def colorize_sprite(sprite, body_color, shadow_color, highlight_color):
    """
    Tint a white/gray sprite to a specific color theme.
    - Bright grays (>180) → highlight color
    - Mid grays (80-180) → body color
    - Dark grays (40-80) → shadow color
    - Very dark (<40) → keep as-is (outlines)
    """
    arr = np.array(sprite).astype(np.float32)
    
    # Only process non-transparent pixels
    alpha = arr[:, :, 3]
    visible = alpha > 10
    
    rgb = arr[:, :, :3]
    brightness = rgb.max(axis=2)
    
    # Create output
    out = arr.copy()
    
    # Highlight pixels (bright white/light gray)
    highlight_mask = visible & (brightness > 200)
    if highlight_mask.any():
        for c in range(3):
            out[:, :, c][highlight_mask] = highlight_color[c]
    
    # Body pixels (mid gray)
    body_mask = visible & (brightness > 80) & (brightness <= 200)
    if body_mask.any():
        for c in range(3):
            # Scale by original brightness for depth
            orig_factor = brightness[body_mask] / 180.0
            out[:, :, c][body_mask] = np.clip(body_color[c] * orig_factor, 0, 255)
    
    # Shadow pixels (darker gray, but not outline-dark)
    shadow_mask = visible & (brightness > 35) & (brightness <= 80)
    if shadow_mask.any():
        for c in range(3):
            out[:, :, c][shadow_mask] = shadow_color[c]
    
    # Keep cheeks pink-ish if they were pink
    # (Detect by checking if red channel was significantly higher than blue)
    r, g, b = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]
    pink_mask = visible & (r > 180) & (b < 180) & (r - b > 60)
    if pink_mask.any():
        out[:, :, 0][pink_mask] = 245
        out[:, :, 1][pink_mask] = 145
        out[:, :, 2][pink_mask] = 155
    
    # Keep red elements (collar, tongue)
    red_mask = visible & (r > 180) & (g < 100) & (b < 100)
    if red_mask.any():
        out[:, :, 0][red_mask] = arr[:, :, 0][red_mask]
        out[:, :, 1][red_mask] = arr[:, :, 1][red_mask]
        out[:, :, 2][red_mask] = arr[:, :, 2][red_mask]
    
    return Image.fromarray(out.astype(np.uint8))


def make_aod(sprite):
    """Create a dimmed grayscale version for Always-On Display."""
    arr = np.array(sprite).astype(np.float32)
    alpha = arr[:, :, 3]
    visible = alpha > 10
    
    rgb = arr[:, :, :3]
    gray = rgb.mean(axis=2)
    
    out = arr.copy()
    if visible.any():
        # Dim grayscale
        dimmed = (gray * 0.5).clip(0, 180)
        for c in range(3):
            out[:, :, c][visible] = dimmed[visible]
    
    return Image.fromarray(out.astype(np.uint8))


def save_sprite(name, sprite, directories):
    """Save sprite to all output directories."""
    for d in directories:
        os.makedirs(d, exist_ok=True)
        path = os.path.join(d, f"{name}.png")
        sprite.save(path)
    print(f"  ✓ {name}.png")


def process_sheet(pet_name, config):
    """Process a sprite sheet into all variants."""
    sheet_path = config["file"]
    poses = config["poses"]
    
    print(f"\n{'='*50}")
    print(f"  Processing: {pet_name}")
    print(f"  Sheet: {os.path.basename(sheet_path)}")
    print(f"{'='*50}")
    
    if not os.path.exists(sheet_path):
        print(f"  ⚠ Sheet not found: {sheet_path}")
        return
    
    img = Image.open(sheet_path)
    print(f"  Sheet size: {img.width}x{img.height}")
    
    # Find sprite regions
    regions = find_sprite_regions(img, num_sprites=len(poses))
    print(f"  Found {len(regions)} sprite regions")
    
    for region in regions:
        print(f"    Region: {region}")
    
    # Process each pose
    out_dirs = [WEAR_DRAWABLE, WATCHFACE_DRAWABLE]
    
    for i, pose in enumerate(poses):
        if i >= len(regions):
            print(f"  ⚠ Not enough regions for pose '{pose}'")
            continue
        
        # Crop and clean
        base_sprite = crop_and_clean(img, regions[i])
        print(f"\n  Pose: {pose} (from region {regions[i]})")
        
        # Save each color theme
        for theme_name, colors in THEMES.items():
            colored = colorize_sprite(
                base_sprite,
                colors["body"], colors["shadow"], colors["highlight"]
            )
            save_sprite(f"{pet_name}_{pose}_{theme_name}", colored, out_dirs)
        
        # Save AOD version (only for idle_1 and sleep)
        if pose in ("idle_1", "sleep"):
            aod = make_aod(base_sprite)
            aod_name = "idle" if pose == "idle_1" else "sleep"
            save_sprite(f"{pet_name}_{aod_name}_aod", aod, out_dirs)


def main():
    print("🐾 WetPet Sprite Processor v4")
    print(f"   Wear drawable: {WEAR_DRAWABLE}")
    print(f"   Watchface drawable: {WATCHFACE_DRAWABLE}")
    
    os.makedirs(WEAR_DRAWABLE, exist_ok=True)
    os.makedirs(WATCHFACE_DRAWABLE, exist_ok=True)
    
    for pet_name, config in SPRITE_SHEETS.items():
        process_sheet(pet_name, config)
    
    print(f"\n✅ All sprites generated!")
    
    # Count output files
    wear_count = len([f for f in os.listdir(WEAR_DRAWABLE) if f.endswith(".png")])
    wf_count = len([f for f in os.listdir(WATCHFACE_DRAWABLE) if f.endswith(".png")])
    print(f"   Wear app: {wear_count} sprites")
    print(f"   Watchface: {wf_count} sprites")


if __name__ == "__main__":
    main()

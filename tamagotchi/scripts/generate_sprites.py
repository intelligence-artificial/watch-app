import os
from PIL import Image

# ── Color Palettes ──
themes = {
    "green": {
        "body": (120, 255, 160, 255),
        "bodyDim": (60, 180, 90, 255),
        "eyes": (40, 40, 50, 255),
        "cheeks": (255, 140, 160, 180),
        "shadow": (60, 60, 70, 200)
    },
    "blue": {
        "body": (100, 200, 255, 255),
        "bodyDim": (50, 120, 180, 255),
        "eyes": (40, 40, 50, 255),
        "cheeks": (255, 140, 160, 180),
        "shadow": (60, 60, 70, 200)
    },
    "pink": {
        "body": (255, 140, 200, 255),
        "bodyDim": (180, 80, 130, 255),
        "eyes": (40, 40, 50, 255),
        "cheeks": (255, 90, 100, 180),
        "shadow": (60, 60, 70, 200)
    },
    "yellow": {
        "body": (255, 230, 100, 255),
        "bodyDim": (180, 150, 50, 255),
        "eyes": (40, 40, 50, 255),
        "cheeks": (255, 140, 160, 180),
        "shadow": (60, 60, 70, 200)
    }
}
transparent = (0, 0, 0, 0)

def get_color(value, palette):
    if value == 1: return palette["body"]
    if value == 2: return palette["bodyDim"]
    if value == 3: return palette["eyes"]
    if value == 4: return palette["cheeks"]
    if value == 5: return palette["shadow"]
    return transparent

def create_image(array_data, filename, palette):
    rows = len(array_data)
    cols = len(array_data[0]) if rows > 0 else 0
    img = Image.new("RGBA", (cols, rows))
    pixels = img.load()
    
    for y in range(rows):
        for x in range(cols):
            pixels[x, y] = get_color(array_data[y][x], palette)
            
    # Scale up using nearest neighbor to make it crisp on watch (e.g. 12x12 -> 144x144)
    scale_factor = 12
    img = img.resize((cols * scale_factor, rows * scale_factor), Image.Resampling.NEAREST)
    img.save(filename)
    print(f"Saved {filename}")

# --- Arrays copied from TamagotchiCanvasRenderer.kt ---
spriteIdle1 = [
    [0,0,0,0,1,1,1,1,0,0,0,0],
    [0,0,0,1,1,1,1,1,1,0,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,1,1,3,1,1,1,1,3,1,1,0],
    [0,1,1,3,1,1,1,1,3,1,1,0],
    [0,1,4,1,1,5,5,1,1,4,1,0],
    [0,1,1,1,1,1,1,1,1,1,1,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,0,1,2,1,1,2,1,0,0,0],
    [0,0,0,1,2,0,0,2,1,0,0,0],
    [0,0,0,2,2,0,0,2,2,0,0,0],
]

spriteIdle2 = [
    [0,0,0,0,0,0,0,0,0,0,0,0],
    [0,0,0,0,1,1,1,1,0,0,0,0],
    [0,0,0,1,1,1,1,1,1,0,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,1,1,3,1,1,1,1,3,1,1,0],
    [0,1,1,3,1,1,1,1,3,1,1,0],
    [0,1,4,1,1,5,5,1,1,4,1,0],
    [0,1,1,1,1,1,1,1,1,1,1,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,0,1,2,1,1,2,1,0,0,0],
    [0,0,0,2,2,0,0,2,2,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,0,0],
]

# Happy: eyes are arched (^_^), little bounce
spriteHappy1 = [
    [0,0,0,0,1,1,1,1,0,0,0,0],
    [0,0,0,1,1,1,1,1,1,0,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,1,1,0,1,1,1,1,0,1,1,0],
    [0,1,1,3,0,1,1,0,3,1,1,0],
    [0,1,4,1,1,5,5,1,1,4,1,0],
    [0,1,1,1,1,1,1,1,1,1,1,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,0,1,2,1,1,2,1,0,0,0],
    [0,0,0,1,2,0,0,2,1,0,0,0],
    [0,0,0,2,2,0,0,2,2,0,0,0],
]

spriteHappy2 = [
    [0,0,0,0,0,0,0,0,0,0,0,0],
    [0,0,0,0,1,1,1,1,0,0,0,0],
    [0,0,0,1,1,1,1,1,1,0,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,1,1,0,1,1,1,1,0,1,1,0],
    [0,1,1,3,0,1,1,0,3,1,1,0],
    [0,1,4,1,1,5,5,1,1,4,1,0],
    [0,1,1,1,1,1,1,1,1,1,1,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,0,1,2,1,1,2,1,0,0,0],
    [0,0,0,2,2,0,0,2,2,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,0,0],
]

# Sleeping: eyes closed as lines, zzZ
spriteSleep = [
    [0,0,0,0,0,0,0,0,0,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,0,0],
    [0,0,0,0,1,1,1,1,0,0,0,0],
    [0,0,0,1,1,1,1,1,1,0,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,1,1,5,5,1,1,5,5,1,1,0],
    [0,1,4,1,1,1,1,1,1,4,1,0],
    [0,1,1,1,1,1,1,1,1,1,1,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,0,2,2,0,0,2,2,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,0,0],
]

# Tired/sad: droopy eyes, slumped
spriteTired = [
    [0,0,0,0,0,0,0,0,0,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,0,0],
    [0,0,0,0,1,1,1,1,0,0,0,0],
    [0,0,0,1,1,1,1,1,1,0,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,1,3,3,1,1,3,3,1,0,0],
    [0,0,1,1,3,1,1,3,1,1,0,0],
    [0,0,4,1,1,5,5,1,1,4,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,0,2,2,0,0,2,2,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,0,0],
]

# Celebrating: arms up! (wider sprite)
spriteCelebrate1 = [
    [0,1,0,0,0,0,0,0,0,0,1,0],
    [0,1,0,0,1,1,1,1,0,0,1,0],
    [0,0,0,1,1,1,1,1,1,0,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,1,1,0,1,1,1,1,0,1,1,0],
    [0,1,1,3,0,1,1,0,3,1,1,0],
    [0,1,4,1,1,5,5,1,1,4,1,0],
    [0,1,1,1,1,1,1,1,1,1,1,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,0,1,2,1,1,2,1,0,0,0],
    [0,0,0,1,2,0,0,2,1,0,0,0],
    [0,0,0,2,2,0,0,2,2,0,0,0],
]

spriteCelebrate2 = [
    [1,0,0,0,0,0,0,0,0,0,0,1],
    [0,1,0,0,1,1,1,1,0,0,1,0],
    [0,0,0,1,1,1,1,1,1,0,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,1,1,0,1,1,1,1,0,1,1,0],
    [0,1,1,3,0,1,1,0,3,1,1,0],
    [0,1,4,1,1,5,5,1,1,4,1,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,0,0,1,2,1,1,2,1,0,0,0],
    [0,0,0,2,2,0,0,2,2,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,0,0],
]

# Output directory
# Output directory (relative to tamagotchi project root)
import pathlib
script_dir = pathlib.Path(__file__).resolve().parent.parent
out_dir = str(script_dir / "watch_faces" / "src" / "main" / "res" / "drawable") + "/"
os.makedirs(out_dir, exist_ok=True)

# Generate colored frames
for color_name, palette in themes.items():
    suffix = f"_{color_name}.png"
    create_image(spriteIdle1, out_dir + "pet_idle_1" + suffix, palette)
    create_image(spriteIdle2, out_dir + "pet_idle_2" + suffix, palette)
    create_image(spriteHappy1, out_dir + "pet_happy_1" + suffix, palette)
    create_image(spriteHappy2, out_dir + "pet_happy_2" + suffix, palette)
    create_image(spriteSleep, out_dir + "pet_sleep" + suffix, palette)
    create_image(spriteTired, out_dir + "pet_tired" + suffix, palette)
    create_image(spriteCelebrate1, out_dir + "pet_celebrate_1" + suffix, palette)
    create_image(spriteCelebrate2, out_dir + "pet_celebrate_2" + suffix, palette)

# Generate 1-bit outlines for Always-On Display (AOD)
# These don't need color variants since AOD is monochrome lines
def create_aod_outline(array_data, filename):
    rows = len(array_data)
    cols = len(array_data[0]) if rows > 0 else 0
    img = Image.new("RGBA", (cols, rows))
    pixels = img.load()
    
    # Simple edge detection based on any non-zero pixel
    for y in range(rows):
        for x in range(cols):
            # Check if this is an edge (filled, but touches an empty space)
            if array_data[y][x] != 0:
                is_edge = False
                # Check neighbors (up, down, left, right)
                if x == 0 or array_data[y][x-1] == 0: is_edge = True
                if x == cols-1 or array_data[y][x+1] == 0: is_edge = True
                if y == 0 or array_data[y-1][x] == 0: is_edge = True
                if y == rows-1 or array_data[y+1][x] == 0: is_edge = True
                
                # Eyes/Features should be drawn too
                if array_data[y][x] in [3, 5]: # Eyes or mouth/shadow
                    is_edge = True
                
                if is_edge:
                    pixels[x, y] = (180, 180, 180, 255) # Dim grey outline
                else:
                    pixels[x, y] = transparent
            else:
                pixels[x, y] = transparent
                
    scale_factor = 12
    img = img.resize((cols * scale_factor, rows * scale_factor), Image.Resampling.NEAREST)
    img.save(filename)
    print(f"Saved (AOD) {filename}")

create_aod_outline(spriteSleep, out_dir + "pet_sleep_aod.png")
create_aod_outline(spriteIdle1, out_dir + "pet_idle_aod.png")


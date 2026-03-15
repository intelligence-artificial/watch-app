# Improve Dog Watch Face Animation

The dog watch face currently has only 2 idle frames from an AI-generated sprite sheet, which get processed into 4 frames with severe visual artifacts. The outlines show white fringing caused by using `Image.NEAREST` resampling and a too-low background removal threshold. The animation appears choppy with only 4 frames cycling at 1fps.

## Proposed Changes

### Sprite Generation

#### [NEW] New Dog Sprite Sheet
- Use image generation to create a new **8-frame dog idle animation** sprite sheet, referencing the existing dog design (pixel-art puppy with floppy ears, red collar, tongue out)
- Frames should show subtle breathing/bobbing/ear-wiggling animation for smooth looping
- Also generate a new sleep frame

---

#### [MODIFY] [generate_sprites.py](file:///home/braindead/github/watch-app/generate_sprites.py)

Fix the root causes of white edge artifacts:

1. **Resampling**: Change `Image.NEAREST` → `Image.LANCZOS` for smoother scaling without jagged edges
2. **Background threshold**: Increase from `35` to `50` to catch more near-black fringe pixels
3. **Alpha edge cleanup**: Add a post-processing step that erodes semi-transparent edge pixels (alpha < 128 → 0) to eliminate fringing
4. **Support 8 poses**: Update `SPRITE_SHEETS["dog"]` to have 8 idle frames instead of 2
5. **Better sprite region detection**: Improve handling for sheets with more sprites (wider sheets with 8+ columns or 2-row grids)

---

### Watch Face Service

#### [MODIFY] [PetComplicationService.kt](file:///home/braindead/github/watch-app/wetpet-watch-app/wear/src/main/java/com/tamagotchi/pet/PetComplicationService.kt)

- Update frame cycling from 4 frames to 8 frames for smoother animation
- Change the ping-pong pattern to a simple sequential loop: `1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → repeat`
- Optionally speed up frame rate from 1fps to 2fps (500ms per frame) for buttery smooth animation

---

### Output Sprites

After processing, the following new drawable files will be generated (per color theme × 4 colors):

| Frame | Example Filename |
|-------|-----------------|
| `dog_idle_1_{color}` | Neutral standing |
| `dog_idle_2_{color}` | Slight bob up |
| `dog_idle_3_{color}` | Ear wiggle |
| `dog_idle_4_{color}` | Slight bob down |
| `dog_idle_5_{color}` | Tail position A |
| `dog_idle_6_{color}` | Slight lean |
| `dog_idle_7_{color}` | Ear wiggle B |
| `dog_idle_8_{color}` | Return to neutral |
| `dog_sleep_{color}` | Sleeping pose |

Total: 36 new dog sprites (8 idle × 4 colors + 1 sleep × 4 colors) + 2 AOD sprites

## Verification Plan

### Manual Verification
1. After generating sprites, visually inspect `dog_idle_*_blue.png` for each frame — verify no white edge artifacts
2. Compare old vs new sprites side-by-side to confirm improvement
3. After building, deploy to watch and verify smooth animation
4. Run `python generate_sprites.py` and confirm all sprites generated without errors

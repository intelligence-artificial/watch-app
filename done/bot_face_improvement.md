# Bot Face Improvement Plan

## Checklist
- [ ] Remove Customize screen routing and entry point from `MainActivity.kt`.
- [ ] Remove Customize button from `HomeScreen.kt`.
- [ ] Delete `CustomizeScreen.kt`.
- [ ] Default `PetType` to `ROBOT` in `PetStateManager.kt` or remove other types.
- [ ] Redesign `generate_robot.py`:
  - Enlarge the screen canvas for a more retro monitor feel (CRT bulging edges).
  - Add more 3D shading to the monitor bezel.
  - Make the phosphor glow much richer (multi-layered blur in Pillow, or layered transparent rectangles since it's pixel art).
  - Enhance facial expressions:
    - **Breathe**: Smoother sub-pixel animation (or just more frames for a 4-frame bounce) and a subtle trailing glow.
    - **Blink**: Smoother closing animation.
    - **Pant**: A highly expressive, 8-bit smiley laughing/panting with an animated mouth (e.g., alternating pixel patterns for teeth/tongue).
    - **Scratch**: A more chaotic glitch effect, tearing the screen with horizontal noise bands.
    - **Sleep**: Cool "Zzz" bubble float animation alongside a darker, "screen saver" dim state.
    - Add a new "booting/loading" state or "calculating" state with a spinner eye if needed (though the current states are Breathe/Blink/Pant/Scratch/Sleep).
- [ ] Execute `generate_robot.py` to overwrite the existing sprites.
- [ ] Add the generated frames to both Wear OS and Watch Face resources.
- [ ] Move this plan to the `done` folder.

## Detailed Plan for Each Checklist Item

### 1. Remove Customize Routing
- Open `MainActivity.kt`.
- Remove the `"customize"` composable navigation route.
- Remove references to `CustomizeScreen`.

### 2. Remove Customize Button
- Open `HomeScreen.kt`.
- Find the `onNavigateToCustomize` references.
- Remove the `Chip` corresponding to "✦ Customize".

### 3. Delete `CustomizeScreen.kt`
- Delete `/home/braindead/github/watch-app/wetpet-watch-app/wear/src/main/java/com/tamagotchi/pet/CustomizeScreen.kt`.

### 4. Hardcode/Default to `ROBOT` 
- Look at `PetStateManager.kt` and `PetComplicationService.kt`.
- Ensure it defaults to `ROBOT`.

### 5. Redesign `generate_robot.py`
- Create a more complex python code using `PIL` to generate highly detailed isometric/retro monitor art.
- Instead of using a simple 36x36 canvas, use a slightly larger or more detailed grid, then nearest-neighbor scale it to 144x144.
- Implement detailed layers:
  - Base shadow.
  - Bezel with specular highlight.
  - Screen area with subtle convex curve.
  - Phosphor overlay.
  - Scanlines and vignette effect.
  - Animated glowing face layers.
- Map the required loops correctly.

### 6. Exec and Deploy
- Run the script and confirm the assets are built.

### 7. Move plan to done
- Using `mv` to move the md file to the `done/` folder.

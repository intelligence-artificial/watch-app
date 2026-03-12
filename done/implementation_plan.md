# Implementation Plan: Update Time Display

## Overview
Updating the WetPet Watchface time display from text/arc elements to an animated dots system.

## Changes

1. **Seconds Layer**: Replace the current thin arc with a single bright green dot (`#50ff78`, radius=5) traveling around an outer ring (r=195).
2. **Minutes Layer**: Implement 60 small dots (cyan `#50e6ff`, radius=4) that appear one by one on a middle ring (r=165). This will be achieved by extending `generate_sprites.py` to print or generate XML blocks with `<Condition>` tags and ghost placeholder dots.
3. **Hours Layer**: Implement a single large neon pink dot (`#ff78b4`, radius=9) for the hour position on an inner ring (r=155).
4. **Cleanup**: 
    - Remove text clock glow layer (`PartText` at y=100 with `#4050ff78`).
    - Remove the old second arc.
    - Shrink/center the digital text clock as a fallback.
    - Retain battery/step arcs and the central pet sprite.

## Dependencies
- Modifying `generate_sprites.py` to output the XML for the 60 minutes dots.
- Updating `watch_face_tamagotchi.xml` with replacements.

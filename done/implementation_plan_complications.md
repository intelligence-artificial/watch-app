# Implementation Plan: Redesign Complications Geometry

## Overview
Based on the screenshot and Fitbit OSS rules, the complications and components need to be aligned using mathematical rules (the Rule of Quarters / symmetric padding on a 384x384 circular canvas) to achieve visual harmony. 

## Calculations & Target Grid
- Canvas: `384x384`, Center: `192, 192`
- Ideal grid rows (centers): `y=96` (Top quarter), `y=192` (Middle/Center), `y=288` (Bottom quarter).
- Ideal grid columns (centers): `x=96` (Left), `x=192` (Center), `x=288` (Right).

## Component Placements
1. **Steps Box (Top Left)**: Move center to `(96, 96)`. 
   - Before: `x="38" y="56"`
   - After: `x="58" y="58"`
2. **HR Box (Top Right)**: Move center to `(288, 96)`.
   - Before: `x="270" y="56"`
   - After: `x="250" y="58"`
3. **Pet Sprite (Top Center)**: Move center down to `(192, 96)` to align with the others forming a perfect horizontal top row.
   - Pet Sprite: `x="152" y="56"` (was `y="105"`)
   - Pet Glow/Touch Box: `x="137" y="41"` (was `y="90"`)
4. **Digital Clock (Center)**:
   - Text Box: `y="162"` (was `214` and `160`). This vertically centers the 60px high box perfectly around `y=192`.
5. **Date (Bottom Third)**:
   - Move box to `y="224"` (was `272`) to sit perfectly between the clock and battery gauge.
6. **Battery Box (Bottom Center)**: Move center to `(192, 288)`.
   - Before: `x="154" y="298"`
   - After: `x="154" y="250"`

## Visual Weight (Stroke Thickness)
The user requested that Fitbit-style data circles be 2x as thick. We will increase the stroke and arc thicknesses:
- `Stroke thickness="2"` -> `4` (on Complication bounding ellipses).
- `Arc thickness="2"` -> `4` (on the Battery / Steps ranged fills).

## Execution
We will write a robust Python patch script that replaces the precise coordinates and stroke thickness elements in `watch_face/src/main/res/raw/watchface.xml`.

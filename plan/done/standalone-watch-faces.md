# Standalone Watch Faces — Moiré + Void Mesh

## Problem

User wanted custom watch faces separate from the voice recorder app.

## Changes

### New module: `watch_faces/`

- **Package**: `com.watchvoice.faces` (separate APK from recorder)
- **`MoireWatchFaceService.kt`**: Two sets of concentric circles with slight offset → optical moiré interference pattern. Offset drifts with minute hand so pattern subtly shifts. White hands, red second hand.
- **`VoidMeshWatchFaceService.kt`**: 3D wireframe sphere with perspective projection. Rotates slowly based on time (Y every ~30min, X tilt over hours). Faint blue-white mesh lines, dots at intersections. White hands, red orbiting second dot.
- **Manifest**: Both registered as proper `WallpaperService` with watch face category
- **Install**: `install_faces.bat` deploys to watch

## Verification

- `gradlew :watch_faces:assembleDebug` — ✅ BUILD SUCCESSFUL

# Fix Watch Face Preview Crash

## Root Cause
`ClassCastException: GradientDrawable cannot be cast to BitmapDrawable`

The Wear OS system UI (`com.google.android.wearable.sysui`) tries to cast the watch face preview drawable to `BitmapDrawable` when switching faces. Our previews were XML `<shape>` drawables (GradientDrawable), which caused the crash.

## Fix
- Deleted `preview_moire.xml` and `preview_void_mesh.xml`
- Replaced with proper PNG bitmap images: `preview_moire.png` and `preview_void_mesh.png`
- Android resolves `@drawable/preview_moire` to the PNG automatically — no manifest changes needed

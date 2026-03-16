# Customize Watchface Plan

## Checklist
- [x] Remove `pixelface` watchface (service and files).
- [x] Investigate and fix customization crash for `pixelface` watchface by overriding `createUserStyleSchema` properly with watch face settings.
- [x] Update hour ring to have 12 dots representing each hour.  
- [x] Change hour ring drawing to use dots instead of a solid ring (dim dots for inactive hours, bright color for active hours).
- [x] Implement color theme customization for `pixelface` offering multiple styles like Grey, Matrix Blue, Matrix Green, and Neon Pink.
- [x] Add the customized theme changes to `PixelFaceWatchFaceService` checking `currentUserStyleRepository`.
- [x] Rebuild and verify build success.

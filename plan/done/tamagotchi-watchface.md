# Tamagotchi Watch Face (たまペット)

## What
Pixel art virtual pet watch face — pet reacts to real health data.

## Pet States
| Mood | Trigger | Animation |
|---|---|---|
| Idle | Default daytime | Gentle bounce, neutral face |
| Happy | Good activity | Fast bounce, ^_^ eyes, floating hearts |
| Celebrating | Step goal hit | Arms up, confetti particles |
| Tired | Battery < 20% | Droopy eyes, sweat drops |
| Sleeping | 11PM - 6AM | Eyes closed, floating zzZ |

## Data Displayed
- Steps (demo values — ready for complications integration)
- Heart rate BPM (demo values)
- Battery % (real via BatteryManager)

## Visual Features
- 12x12 pixel art sprites (10 frames across 5 moods)
- Particle system (hearts, confetti, zzZ, sweat drops)
- CRT scan lines
- Neon-on-black OLED theme
- Second dot ring
- Corner bracket decorations

## Next Steps
- Integrate real health data via ComplicationSlots
- Add tap-to-pet interaction

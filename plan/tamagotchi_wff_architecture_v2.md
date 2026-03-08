# Architectural Plan: High-Density Tamagotchi Watch Face (WFF)

This plan outlines the complete rewrite of the existing Kotlin/Canvas-based Tamagotchi watch face (`TamagotchiWatchFaceService.kt`) into the declarative **Watch Face Format (WFF)**. 

## Rationale for Rewrite
The current implementation actively draws pixels in a Kotlin rendering loop at ~20fps using `CanvasRenderer`. This keeps the main application processor (AP) awake, leading to severe battery drain. WFF completely offloads rendering to the Wear OS Microcontroller Unit (MCU) coprocessor.

---

## 1. Core Architecture: MCU-First Rendering
The app will be split into two distinct layers:
*   **Declarative UI (WFF XML):** The visual layout, animations, pixel art rendering, and data binding will be defined entirely in `res/xml/watch_face.xml`. The Wear OS WFF engine parses this and runs it on the MCU.
*   **Kotlin Background Service (`HealthDataManager`):** The Kotlin application will *only* handle long-term logic, such as passively reacting to Android Health Services (AHS) step updates, calculating the pet's state (Hunger, Energy, XP), and pushing those values to WFF user configurations or complication providers.

## 2. Health Data & Game Logic Processing
*   **Passive Monitoring:** Replace real-time polling with AHS `PassiveMonitoringClient`. Process steps and heart rate in background batches.
*   **Time-Delta State Resolution:** When the Kotlin service wakes up, it calculates time elapsed, applies batched steps, and updates the pet's internal state.
*   **Data Bridging:** WFF cannot execute custom Kotlin logic directly. The updated pet state (Mood, Frame Index, Hunger Level) will be bridged to the XML layer using either native Data Sources (if applicable) or by exposing them as Custom Complications that the WFF XML consumes.

## 3. Strict Battery Saver Mode (Under 15%)
Wear OS provides system text sources for battery level. WFF allows conditional rendering based on this data.
*   **Conditional Rendering:** Use `<Condition>` tags in WFF checking `[BATTERY_CURRENT]`.
*   **Under 15% Trigger:** 
    *   Hide the central `<PartAnimatedImage>` (Tamagotchi sprite sheet).
    *   Hide all heavy graphics (radial gauges, background glow).
    *   Show a fallback monochrome `<PartText>` digital clock and simple step counter to maximize remaining battery life.

## 4. UI Layout & Customization
*   **Center Hero:** Use a `<PartAnimatedImage>` for the pet. The current 12x12 Kotlin arrays will be exported as a single WebP sprite sheet or WEBP animation file. Animation will run at a max of 15 FPS.
*   **Concentric Radial Arcs:** Surround the pet with `<PartDraw>` radial progress bars. For example, a top arc binding to `[BATTERY_CURRENT]` and a bottom arc binding to daily step progress.
*   **Four Quadrants (Complications):** Define four `<ComplicationSlot>` elements in the corners (e.g., Top Left, Top Right).
*   **User Customization:** Define `<UserConfiguration>` options in XML allowing users to assign standard system complications or custom pet data complications to the slots.

## 5. Always-On Display (AOD) Strategy
WFF heavily restricts AOD rendering to save power and prevent burn-in.
*   **Animation Pausing:** WFF automatically pauses `<PartAnimatedImage>` in AOD. The XML will be structured so the default paused frame is the "Sleeping" pet sprite.
*   **OPR Reduction System:** Use `<Variant mode="ambient">` to drastically reduce on-pixel ratio (OPR).
    *   Change stroke colors to dim grey.
    *   Swap the filled pet sprite for a 1-bit wireframe outline image.
    *   Hide seconds indicators and fast-moving particles. 

## Next Implementation Steps
1.  **Asset Generation:** Convert the multi-dimensional arrays in `TamagotchiCanvasRenderer.kt` into actual `.png` or `.webp` image assets (Idle, Happy, Sleep, Tired, Celebrate).
2.  **XML Scaffolding:** Create the root `watch_face.xml` and define the basic layout (clock `<PartText>` and complication slots).
3.  **WFF Data Binding:** Wire the WFF XML `<Condition>` logic to battery levels and complication slot data.
4.  **Kotlin Refactor:** Strip out all Canvas rendering code from `TamagotchiWatchFaceService.kt`. Repurpose it as a lightweight foreground/background service purely for health data aggregation.

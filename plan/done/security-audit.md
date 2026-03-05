# Security Audit: Watch Voice Recorder

## Date: 2026-03-05

## Scan Results

### Secrets & Credentials

- [x] No API keys found
- [x] No hardcoded passwords
- [x] No `.env` files
- [x] No hardcoded IP addresses in app code
- [x] No keystores committed

### Network Security

- [x] No `android:usesCleartextTraffic` enabled
- [x] No `android:debuggable` hardcoded in manifests
- [x] Data Layer API (watch↔phone) uses Google Play Services encrypted channel
- [x] INTERNET permission used only for GMS Wearable sync (no custom HTTP)

### Data Protection

- [x] Voice recordings stored in app-private `filesDir` (not external storage)
- [x] Notes JSON stored in app-private `filesDir`
- [x] `.gitignore` covers: recordings, ML models, build outputs, keystores, env files

### Off-WiFi Security

- Watch↔Phone sync uses **Bluetooth LE** and **WiFi Direct** (not your home WiFi)
- Data Layer API is encrypted by Google Play Services
- No internet connection required — everything works offline via Bluetooth
- ✅ **Safe to use anywhere** — no cloud services, no external APIs

### Debug Notification

- The "debugging application" notification is an **Android OS system-level notification**
- It appears because the APK is built as `debug` variant
- **Cannot be removed from code** — it requires signing a release APK
- To remove: create a signing keystore and run `assembleRelease` instead of `assembleDebug`

## .gitignore Coverage

- Build outputs (`wear/build/`, `mobile/build/`, `.gradle/`)
- ML models (`mobile/src/main/assets/model/`, `*.bin`)
- Voice recordings (`recordings/`, `*.m4a`, `*.wav`)
- Third-party clones (`mobile/whisper.cpp/`)
- Native build files (`*.so`, `*.o`, `.cxx/`)
- Security files (`*.keystore`, `*.jks`, `*.pem`, `*.key`, `.env`)
- IDE files (`.idea/`, `*.iml`, `.vscode/`)
- Debug scripts (`debug_phone.bat`, `debug_watch.bat`)

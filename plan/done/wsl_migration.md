# WSL Migration Plan — Batch → Shell Scripts

## Overview

The project has 10 `.bat` files that only work on Windows. To develop entirely in WSL, we need bash `.sh` equivalents. The Gradle project itself (Kotlin DSL, Android SDK) is cross-platform — only the helper scripts need porting.

## Key Changes

### 1. Generate `gradlew` (Unix Gradle Wrapper)
The project has `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` but no `gradlew` Unix script. We'll generate it using `gradle wrapper` or create the standard script.

### 2. SDK Path Convention
Windows scripts use `%LOCALAPPDATA%\Android\Sdk`. On WSL/Linux the convention is `$HOME/Android/Sdk`. We'll use `$ANDROID_HOME` env var with a fallback to `$HOME/Android/Sdk`.

For Java: the scripts point at `%LOCALAPPDATA%\openjdk-17\jdk-17.0.2`. On Linux, we'll rely on whatever `java` is on `$PATH` (or `$JAVA_HOME` if set), since JDK is typically installed via `apt` or SDKMAN.

### 3. Scripts to Convert

| Windows Script          | Linux Equivalent        | Notes |
|------------------------|------------------------|-------|
| `setup_sdk.bat`        | `setup_sdk.sh`         | Download linux cmdline-tools & JDK |
| `build.bat`            | `build.sh`             | Use `./gradlew` instead of `gradlew.bat` |
| `run.bat`              | `run.sh`               | Calls build then install |
| `install.bat`          | `install.sh`           | Detect watch vs phone via `adb` |
| `install_phone.bat`    | `install_phone.sh`     | Install to phone only |
| `install_faces.bat`    | `install_faces.sh`     | Install watch faces APK |
| `debug_phone.bat`      | `debug_phone.sh`       | logcat filter |
| `debug_watch.bat`      | `debug_watch.sh`       | logcat filter |
| `pull_recordings.bat`  | `pull_recordings.sh`   | Pull .m4a files from watch |
| `wireless_setup.bat`   | `wireless_setup.sh`    | Wireless ADB setup |

### 4. Line Endings
All existing `.bat` files have `\r\n`. New `.sh` files must use `\n` (Unix LF). We'll also add `*.sh` to `.gitattributes` with `eol=lf`.

## Verification

- Run `shellcheck` on all generated `.sh` files (if available)
- Verify `./gradlew --version` works after generating the wrapper
- User manually tests `build.sh` with a connected device

## Checklist

- [ ] Generate `gradlew` Unix wrapper script
- [ ] Create `setup_sdk.sh`
- [ ] Create `build.sh`
- [ ] Create `run.sh`
- [ ] Create `install.sh`
- [ ] Create `install_phone.sh`
- [ ] Create `install_faces.sh`
- [ ] Create `debug_phone.sh`
- [ ] Create `debug_watch.sh`
- [ ] Create `pull_recordings.sh`
- [ ] Create `wireless_setup.sh`
- [ ] Update `.gitattributes` for `.sh` line endings
- [ ] Make all `.sh` files executable
- [ ] Verify `./gradlew --version`

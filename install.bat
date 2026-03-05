@echo off
setlocal enabledelayedexpansion

echo.
echo +============================================================+
echo :          Watch Voice Recorder - Install                     :
echo +============================================================+
echo.

:: Set up paths
set JAVA_HOME=%LOCALAPPDATA%\openjdk-17\jdk-17.0.2
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%

:: Set APK path
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk

:: Check if APK exists
if not exist "%APK_PATH%" (
    echo [ERROR] APK not found at %APK_PATH%
    echo         Please run build.bat first.
    exit /b 1
)

:: Check for ADB
if not exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    echo [ERROR] ADB not found. Run setup_sdk.bat first.
    exit /b 1
)

echo [INFO] Checking for connected devices...
echo.

:: List devices
adb devices -l

:: Count connected devices
for /f "tokens=*" %%a in ('adb devices ^| find /c "device"') do set DEVICE_COUNT=%%a
set /a DEVICE_COUNT=DEVICE_COUNT-1

if %DEVICE_COUNT% lss 1 (
    echo.
    echo [ERROR] No devices connected.
    echo.
    echo   To connect your Pixel Watch 2:
    echo   1. Enable Developer Options on the watch
    echo   2. Enable ADB debugging
    echo   3. Connect via USB or run wireless_setup.bat
    echo.
    exit /b 1
)

echo.
echo [INFO] Installing APK...
adb install -r "%APK_PATH%"

if %errorlevel%==0 (
    echo.
    echo [SUCCESS] APK installed successfully!
    echo.
    echo [INFO] Launching app...
    adb shell am start -n com.watchvoice.recorder/.MainActivity

    echo.
    echo +============================================================+
    echo :             App launched on watch!                           :
    echo +============================================================+
    echo.
) else (
    echo.
    echo [ERROR] Installation failed.
    echo         Make sure ADB debugging is enabled on your watch.
    exit /b 1
)

endlocal

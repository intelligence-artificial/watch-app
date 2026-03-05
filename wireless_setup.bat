@echo off
setlocal enabledelayedexpansion

echo.
echo +============================================================+
echo :       Watch Voice Recorder - Wireless ADB Setup             :
echo +============================================================+
echo.
echo This script helps you set up wireless debugging with your Pixel Watch 2.
echo.
echo PREREQUISITES:
echo   1. Watch connected via USB with ADB debugging enabled
echo   2. Watch and PC on the same WiFi network
echo.

:: Check for ADB
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set PATH=%ANDROID_HOME%\platform-tools;%PATH%

where adb >nul 2>&1
if %errorlevel% neq 0 (
    if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
        set PATH=%ANDROID_HOME%\platform-tools;%PATH%
    ) else (
        echo [ERROR] ADB not found. Install Android SDK platform-tools.
        exit /b 1
    )
)

echo [STEP 1] Current devices:
adb devices -l
echo.

echo [STEP 2] Enabling TCP/IP mode on port 5555...
adb tcpip 5555

echo.
echo [STEP 3] Getting device IP address...
echo.

:: Try to get IP address from device
for /f "tokens=9" %%a in ('adb shell ip route ^| findstr "wlan0"') do (
    set DEVICE_IP=%%a
)

if defined DEVICE_IP (
    echo   Detected IP: %DEVICE_IP%
    echo.
    echo [STEP 4] You can now disconnect the USB cable.
    echo.
    echo   To connect wirelessly, run:
    echo   adb connect %DEVICE_IP%:5555
    echo.

    set /p CONNECT="Connect now? (Y/N): "
    if /i "!CONNECT!"=="Y" (
        echo.
        echo [INFO] Connecting to %DEVICE_IP%:5555...
        timeout /t 3 /nobreak >nul
        adb connect %DEVICE_IP%:5555
        echo.
        echo [INFO] Verifying connection:
        adb devices -l
    )
) else (
    echo   Could not auto-detect IP address.
    echo.
    echo   Find your watch's IP address:
    echo   Settings ^> Connectivity ^> Wi-Fi ^> your network ^> IP address
    echo.
    set /p MANUAL_IP="Enter device IP: "
    if defined MANUAL_IP (
        echo.
        echo [INFO] Connecting to !MANUAL_IP!:5555...
        timeout /t 3 /nobreak >nul
        adb connect !MANUAL_IP!:5555
        echo.
        adb devices -l
    )
)

echo.
echo ================================================================
echo   Wireless ADB setup complete!
echo   You can now run install.bat without USB cable.
echo ================================================================
echo.

endlocal

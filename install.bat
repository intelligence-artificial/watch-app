@echo off
setlocal enabledelayedexpansion

echo.
echo +============================================================+
echo :          Watch Voice Recorder - Install All                 :
echo +============================================================+
echo.

:: Set up paths
set JAVA_HOME=%LOCALAPPDATA%\openjdk-17\jdk-17.0.2
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%

:: Check for ADB
if not exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    echo [ERROR] ADB not found. Run setup_sdk.bat first.
    exit /b 1
)

echo [INFO] Connected devices:
adb devices -l
echo.

:: Parse connected devices — use "adb devices" (short form) to get serials
set WATCH_SERIAL=
set PHONE_SERIAL=
set DEVICE_COUNT=0

for /f "tokens=1,2" %%A in ('adb devices 2^>nul') do (
    if "%%B"=="device" (
        set /a DEVICE_COUNT+=1
        set _SERIAL=%%A
        :: Query the device model to determine type
        for /f "delims=" %%M in ('adb -s %%A shell getprop ro.product.model 2^>nul') do (
            set _MODEL=%%M
        )
        echo [INFO] Found: !_SERIAL! ^(!_MODEL!^)
        echo !_MODEL! | findstr /I /C:"Watch" >nul 2>&1
        if !errorlevel!==0 (
            set WATCH_SERIAL=!_SERIAL!
        ) else (
            set PHONE_SERIAL=!_SERIAL!
        )
    )
)

if %DEVICE_COUNT%==0 (
    echo [ERROR] No devices found. Connect a device and try again.
    exit /b 1
)

echo.

:: Install watch APK (only to watch device)
set WEAR_APK=wear\build\outputs\apk\debug\wear-debug.apk
if exist "%WEAR_APK%" (
    if not "%WATCH_SERIAL%"=="" (
        echo [INFO] Installing watch APK to %WATCH_SERIAL%...
        adb -s %WATCH_SERIAL% install -r "%WEAR_APK%"
        if !errorlevel!==0 (
            echo [SUCCESS] Watch APK installed!
            adb -s %WATCH_SERIAL% shell am start -n com.watchvoice.recorder/.MainActivity
        ) else (
            echo [WARNING] Watch APK install failed.
        )
    ) else (
        echo [SKIP] No watch found - skipping watch APK.
    )
) else (
    echo [SKIP] Watch APK not found. Run: gradlew :wear:assembleDebug
)

echo.

:: Install phone APK (only to phone device)
set MOBILE_APK=mobile\build\outputs\apk\debug\mobile-debug.apk
if exist "%MOBILE_APK%" (
    if not "%PHONE_SERIAL%"=="" (
        echo [INFO] Installing phone APK to %PHONE_SERIAL%...
        adb -s %PHONE_SERIAL% install -r "%MOBILE_APK%"
        if !errorlevel!==0 (
            echo [SUCCESS] Phone APK installed!
            adb -s %PHONE_SERIAL% shell am start -n com.watchvoice.recorder/.MainActivity
        ) else (
            echo [WARNING] Phone APK install failed.
        )
    ) else (
        echo [SKIP] No phone found - skipping phone APK.
        echo        ^(This prevents installing the phone app on the watch^)
    )
) else (
    echo [SKIP] Phone APK not found. Run: gradlew :mobile:assembleDebug
)

echo.
echo +============================================================+
echo :                   Install Complete                           :
echo +============================================================+
echo.

endlocal

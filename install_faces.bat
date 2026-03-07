@echo off
setlocal enabledelayedexpansion

echo.
echo +============================================================+
echo :          Watch Faces - Install                               :
echo +============================================================+
echo.

:: Set up paths (same as install.bat)
set JAVA_HOME=%LOCALAPPDATA%\openjdk-17\jdk-17.0.2
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%

:: Check for ADB
if not exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    echo [ERROR] ADB not found. Run setup_sdk.bat first.
    exit /b 1
)

:: Find the APK
set APK=watch_faces\build\outputs\apk\debug\watch_faces-debug.apk
if not exist "%APK%" (
    echo [ERROR] APK not found. Run: gradlew :watch_faces:assembleDebug
    exit /b 1
)

echo [INFO] Connected devices:
adb devices -l
echo.

:: Find watch device
set WATCH_SERIAL=

for /f "tokens=1,2" %%A in ('adb devices 2^>nul') do (
    if "%%B"=="device" (
        set _SERIAL=%%A
        for /f "delims=" %%M in ('adb -s %%A shell getprop ro.product.model 2^>nul') do (
            set _MODEL=%%M
        )
        echo [INFO] Found: !_SERIAL! ^(!_MODEL!^)
        echo !_MODEL! | findstr /I /C:"Watch" >nul 2>&1
        if !errorlevel!==0 (
            set WATCH_SERIAL=!_SERIAL!
        )
    )
)

if "%WATCH_SERIAL%"=="" (
    echo [ERROR] No watch found. Connect your watch and try again.
    exit /b 1
)

echo.
echo [INFO] Installing watch faces to %WATCH_SERIAL%...
adb -s %WATCH_SERIAL% install -r "%APK%"

if %errorlevel%==0 (
    echo.
    echo [SUCCESS] Watch faces installed!
    echo.
    echo To select a watch face:
    echo   1. Long-press on your current watch face
    echo   2. Swipe to browse watch faces
    echo   3. Look for "Moire" or "Void Mesh"
) else (
    echo [ERROR] Install failed.
)

echo.
echo +============================================================+
echo :                   Install Complete                           :
echo +============================================================+
echo.

endlocal

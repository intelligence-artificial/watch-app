@echo off
setlocal enabledelayedexpansion

echo.
echo +============================================================+
echo :          Voice Notes - Install Phone App                    :
echo +============================================================+
echo.

:: Set up paths
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set PATH=%ANDROID_HOME%\platform-tools;%PATH%

set MOBILE_APK=mobile\build\outputs\apk\debug\mobile-debug.apk

if not exist "%MOBILE_APK%" (
    echo [ERROR] Phone APK not found at %MOBILE_APK%
    echo         Please run build.bat first.
    exit /b 1
)

echo [INFO] Connected devices:
adb devices -l
echo.

:: Find a non-watch device serial (phone)
set "PHONE_SERIAL="
for /f "tokens=1,*" %%a in ('adb devices -l ^| findstr /v "Watch watch eos" ^| findstr "device product:"') do (
    set "PHONE_SERIAL=%%a"
)

if "!PHONE_SERIAL!"=="" (
    echo [ERROR] No phone device found. Only watch connected?
    echo         Connect your phone via USB or wireless ADB.
    exit /b 1
)

echo [INFO] Using phone: !PHONE_SERIAL!
echo [INFO] Installing phone APK...
adb -s !PHONE_SERIAL! install -r "%MOBILE_APK%"

if %errorlevel%==0 (
    echo.
    echo [SUCCESS] Phone APK installed!
    echo [INFO] Launching app...
    adb -s !PHONE_SERIAL! shell am start -n com.watchvoice.recorder/.MainActivity
    echo.
) else (
    echo.
    echo [ERROR] Installation failed.
    exit /b 1
)

endlocal

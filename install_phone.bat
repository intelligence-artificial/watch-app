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

echo [INFO] Installing phone APK...
adb install -r "%MOBILE_APK%"

if %errorlevel%==0 (
    echo.
    echo [SUCCESS] Phone APK installed!
    echo [INFO] Launching app...
    adb shell am start -n com.watchvoice.recorder/.MainActivity
    echo.
) else (
    echo.
    echo [ERROR] Installation failed.
    exit /b 1
)

endlocal

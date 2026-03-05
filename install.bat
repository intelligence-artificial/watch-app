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

:: Install watch APK
set WEAR_APK=wear\build\outputs\apk\debug\wear-debug.apk
if exist "%WEAR_APK%" (
    echo [INFO] Installing watch APK...
    adb install -r "%WEAR_APK%"
    if %errorlevel%==0 (
        echo [SUCCESS] Watch APK installed!
        adb shell am start -n com.watchvoice.recorder/.MainActivity
    ) else (
        echo [WARNING] Watch APK install failed - watch may not be connected
    )
) else (
    echo [SKIP] Watch APK not found. Run build.bat first.
)

echo.

:: Install phone APK
set MOBILE_APK=mobile\build\outputs\apk\debug\mobile-debug.apk
if exist "%MOBILE_APK%" (
    echo [INFO] Installing phone APK...
    adb install -r "%MOBILE_APK%"
    if %errorlevel%==0 (
        echo [SUCCESS] Phone APK installed!
        adb shell am start -n com.watchvoice.recorder/.MainActivity
    ) else (
        echo [WARNING] Phone APK install failed - phone may not be connected
    )
) else (
    echo [SKIP] Phone APK not found. Run build.bat first.
)

echo.
echo +============================================================+
echo :                   Install Complete                           :
echo +============================================================+
echo.

endlocal

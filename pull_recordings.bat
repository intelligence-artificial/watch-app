@echo off
setlocal enabledelayedexpansion

echo.
echo +============================================================+
echo :       Watch Voice Recorder - Pull Recordings                :
echo +============================================================+
echo.

:: Set up paths
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set PATH=%ANDROID_HOME%\platform-tools;%PATH%

:: Create local recordings directory
if not exist "recordings" mkdir recordings

:: Check for ADB
if not exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    echo [ERROR] ADB not found. Run setup_sdk.bat first.
    exit /b 1
)

echo [INFO] Checking for connected devices...
adb devices -l
echo.

:: Pull all .m4a files from app internal storage
echo [INFO] Pulling recordings from watch...
echo.

:: The app stores files in /data/data/com.watchvoice.recorder/files/
adb shell "run-as com.watchvoice.recorder ls files/*.m4a 2>/dev/null" > "%TEMP%\watch_recordings.txt" 2>nul

set FILE_COUNT=0
for /f "tokens=*" %%f in (%TEMP%\watch_recordings.txt) do (
    set /a FILE_COUNT+=1
    echo   Pulling: %%~nxf
    adb shell "run-as com.watchvoice.recorder cat files/%%~nxf" > "recordings\%%~nxf"
)

if %FILE_COUNT%==0 (
    echo [INFO] No recordings found on watch.
    echo.
    echo   Make sure:
    echo   1. The app is installed on the watch
    echo   2. You have made at least one recording
    echo.
) else (
    echo.
    echo +============================================================+
    echo :   Pulled %FILE_COUNT% recording(s) to .\recordings\              :
    echo +============================================================+
    echo.
    echo   Files saved in: %CD%\recordings\
    echo.
    echo   You can now transcribe these with any speech-to-text tool:
    echo   - OpenAI Whisper: whisper recordings\*.m4a
    echo   - Google Cloud: gcloud ml speech recognize recordings\*.m4a
    echo   - Or any local LLM with audio support
    echo.
)

del "%TEMP%\watch_recordings.txt" 2>nul
endlocal

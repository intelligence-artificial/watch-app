@echo off
setlocal enabledelayedexpansion

echo.
echo +============================================================+
echo :          Watch Voice Recorder - Setup SDK                   :
echo +============================================================+
echo.

:: Set SDK location
set SDK_ROOT=%LOCALAPPDATA%\Android\Sdk
set CMDLINE_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip
set JDK_URL=https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_windows-x64_bin.zip

:: Create SDK directory
if not exist "%SDK_ROOT%" mkdir "%SDK_ROOT%"
if not exist "%SDK_ROOT%\cmdline-tools" mkdir "%SDK_ROOT%\cmdline-tools"

:: Download and Extract JDK if missing
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [INFO] Downloading OpenJDK 17...
    curl.exe -L -o "%TEMP%\openjdk17.zip" %JDK_URL%
    if not exist "%LOCALAPPDATA%\openjdk-17" mkdir "%LOCALAPPDATA%\openjdk-17"
    powershell -Command "Expand-Archive -Path '%TEMP%\openjdk17.zip' -DestinationPath '%LOCALAPPDATA%\openjdk-17' -Force"
    for /d %%i in ("%LOCALAPPDATA%\openjdk-17\jdk*") do set "JAVA_HOME=%%i"
    set "PATH=!JAVA_HOME!\bin;%PATH%"
    echo [INFO] Using Java at !JAVA_HOME!
)

:: Download and Extract cmdline-tools
echo [INFO] Downloading Command Line Tools...
curl.exe -L -o "%TEMP%\cmdline-tools.zip" %CMDLINE_TOOLS_URL%
powershell -Command "Expand-Archive -Path '%TEMP%\cmdline-tools.zip' -DestinationPath '%SDK_ROOT%\cmdline-tools' -Force"

:: Organize for sdkmanager (move to 'latest')
if exist "%SDK_ROOT%\cmdline-tools\latest" rmdir /s /q "%SDK_ROOT%\cmdline-tools\latest"
move "%SDK_ROOT%\cmdline-tools\cmdline-tools" "%SDK_ROOT%\cmdline-tools\latest"

:: Install packages (API 33 for Wear OS)
echo [INFO] Installing Android SDK packages...
set SDK_BIN=%SDK_ROOT%\cmdline-tools\latest\bin
echo y | "%SDK_BIN%\sdkmanager.bat" --licenses
call "%SDK_BIN%\sdkmanager.bat" "platform-tools" "build-tools;34.0.0" "platforms;android-34" "platforms;android-33"

echo.
echo [SUCCESS] SDK setup complete!
echo.
endlocal

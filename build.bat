@echo off
setlocal enabledelayedexpansion

echo.
echo +============================================================+
echo :          Watch Voice Recorder - Build                       :
echo +============================================================+
echo.

:: Set up Java and Android SDK paths
set JAVA_HOME=%LOCALAPPDATA%\openjdk-17\jdk-17.0.2
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools;%PATH%

:: Check for Java
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Java not found at %JAVA_HOME%
    echo         Run setup_sdk.bat first to install JDK and Android SDK.
    exit /b 1
)

:: Check for Android SDK
if not exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    echo [ERROR] Android SDK not found at %ANDROID_HOME%
    echo         Run setup_sdk.bat first to install JDK and Android SDK.
    exit /b 1
)

echo [INFO] Java: %JAVA_HOME%
echo [INFO] Android SDK: %ANDROID_HOME%
echo.

:: Download gradle-wrapper.jar if missing
if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo [INFO] Downloading Gradle wrapper JAR...
    if not exist "gradle\wrapper" mkdir gradle\wrapper
    curl -L -o gradle\wrapper\gradle-wrapper.jar https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar
    if not exist "gradle\wrapper\gradle-wrapper.jar" (
        echo [ERROR] Failed to download gradle-wrapper.jar
        exit /b 1
    )
    echo [INFO] Gradle wrapper downloaded successfully
)

echo [INFO] Building debug APK...
echo.

:: Run Gradle build
call gradlew.bat assembleDebug --no-daemon

if %errorlevel%==0 (
    echo.
    echo +============================================================+
    echo :                   BUILD SUCCESSFUL                          :
    echo +============================================================+
    echo.
    echo   APK Location:
    echo   app\build\outputs\apk\debug\app-debug.apk
    echo.
) else (
    echo.
    echo +============================================================+
    echo :                    BUILD FAILED                             :
    echo +============================================================+
    echo.
    echo   Check the error messages above.
    echo.
    exit /b 1
)

endlocal

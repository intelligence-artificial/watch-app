@echo off
cd /d "%~dp0"
call build.bat
if %errorlevel%==0 call install.bat

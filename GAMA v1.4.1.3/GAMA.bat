:: EASY CONTROLS

set version=v1.4.1.3

:: ESSENTIALS
@echo off
setlocal EnableDelayedExpansion
title GAMA %version%
set CYAN=[96m
set RESET=[0m
cls

:: MAIN MENU
:main_menu
mode con: cols=58 lines=34
cls

:: Check for any ADB devices
set adb_status=Disconnected
for /f "tokens=1" %%A in ('"%~dp0\adb" get-state 2^>nul') do (
    if "%%A"=="device" (
        set adb_status=Connected
    )
)

:: Check for updates
set CURRENT_VERSION=%version%
set "VERSION_FILE=%TEMP%\gama_latest_version.txt"
curl -s -o "%VERSION_FILE%" https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/version.txt

if exist "%VERSION_FILE%" (
    set /p LATEST_VERSION=<"%VERSION_FILE%"

    if "!LATEST_VERSION!" NEQ "%CURRENT_VERSION%" (
        set checkforupdates_display=New update available!
    ) else (
        set checkforupdates_display=You're on the latest version
    )
) else (
    set checkforupdates_display=Update check failed
)

:: UI
cls
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo %CYAN%     GPU API Manager for Android-based devices %RESET%
echo %CYAN%     [GAMA v%CURRENT_VERSION%] %RESET%
echo      Device status: !adb_status!
echo      Update availability: !checkforupdates_display!
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo      GAMA is standing by and awaiting your command. 
echo      What's next?
echo.
echo %CYAN%     Core features %RESET%
echo      Switch graphics API to:
echo          [1] Vulkan 
echo          [2] OpenGL
echo      [3] Launch all installed apps
echo.
echo %CYAN%     Extra tools and resources %RESET%
echo      [4] Visit GAMA's GitHub repository
echo      [5] Refresh main menu
echo          - Re-checks for updates
echo          - Scans for connected devices
echo      [6] Start Shizuku
echo      [X] Exit
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo      Enter your choice: 
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
choice /c 123456X /n >nul
goto screen%ERRORLEVEL%

:screen1
call bin\set_vulkan.bat
goto :main_menu

:screen2
call bin\set_opengl.bat
goto :main_menu

:screen3
call bin\launch_all_apps.bat
goto :main_menu

:screen4
start https://github.com/popovicialinc/gama
goto :main_menu

:screen5
goto :main_menu

:screen6
call bin\shizuku.bat
goto :main_menu

:screen7
exit /b

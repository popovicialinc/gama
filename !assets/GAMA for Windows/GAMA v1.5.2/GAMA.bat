@echo off
setlocal EnableDelayedExpansion
set version=1.5.2
title GAMA v%version%

:: --- CONFIG ---
set "ADB_EXE=%~dp0bin\adb.exe"
if not exist "!ADB_EXE!" set "ADB_EXE=adb"

:: Colors
set CYAN=[96m
set RESET=[0m

:main_menu
mode con: cols=58 lines=37
cls

:: --- ADB CHECK ---
set adb_status=%RED%Disconnected%RESET%
set "is_connected=false"
for /f "tokens=2" %%A in ('""!ADB_EXE!" devices 2>nul | findstr /v "List""') do (
if "%%A"=="device" (
set adb_status=Connected%RESET%
set "is_connected=true"
)
if "%%A"=="unauthorized" set adb_status=%RED%Unauthorized%RESET%
)

:: --- API CHECK ---
set "current_api=N/A"
if "!is_connected!"=="true" (
for /f "tokens=*" %%A in ('""!ADB_EXE!" shell getprop debug.hwui.renderer 2>nul"') do set "current_api=%%A"
if "!current_api!"=="" set "current_api=Default (System)"
if "!current_api!"=="skiavk" set "current_api=Vulkan"
if "!current_api!"=="opengl" set "current_api=OpenGL"
)

:: --- UPDATE CHECK ---
set "checkforupdates_display=Checking..."
if exist "%TEMP%\gama_ver.txt" del "%TEMP%\gama_ver.txt"
curl -s -m 2 -o "%TEMP%\gama_ver.txt" https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/version.txt
if exist "%TEMP%\gama_ver.txt" (
set /p LATEST_VERSION=<"%TEMP%\gama_ver.txt"
if "!LATEST_VERSION!" NEQ "%version%" (
set checkforupdates_display=New version available!%RESET%
) else (
set checkforupdates_display=You're on the latest version
)
) else (
set checkforupdates_display=%RED%Check failed%RESET%
)

:: --- UI ---

echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo %CYAN%     GPU API Manager for Android-based devices %RESET%
echo %CYAN%     [GAMA v%version%] %RESET%
echo.
echo      Device status: !adb_status!
echo      Active GPU API: %CYAN%!current_api!%RESET%
echo      Update availability: !checkforupdates_display!
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo      GAMA is standing by and awaiting your command.
echo      What's next?
echo.
echo %CYAN%     Core Features%RESET%
echo      Switch graphics API to:
echo          [1] Vulkan
echo          [2] OpenGL
echo      [3] Launch all installed apps
echo.
echo %CYAN%     Extra tools and resources %RESET%
echo      [4] Visit GAMA's GitHub repository
echo      [5] Refresh main menu
echo          - Checks for available updates
echo          - Scans for connected devices
echo      [6] Start Shizuku
echo      [X] Exit
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo      Enter your choice -
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
choice /c 123456X /n /m "     "

if %ERRORLEVEL%==1 call bin\set_renderer.bat skiavk
if %ERRORLEVEL%==2 call bin\set_renderer.bat opengl
if %ERRORLEVEL%==3 call bin\launch_all_apps.bat
if %ERRORLEVEL%==4 start https://github.com/popovicialinc/gama
if %ERRORLEVEL%==5 goto main_menu
if %ERRORLEVEL%==6 call bin\shizuku.bat
if %ERRORLEVEL%==7 goto exit_cleanup

goto main_menu

:exit_cleanup
"!ADB_EXE!" kill-server >nul 2>&1
exit /b
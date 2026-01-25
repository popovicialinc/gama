@echo off
setlocal EnableDelayedExpansion

:: define colors
set CYAN=[96m
set RESET=[0m
title Setting renderer...

:: receive argument from main menu (skiavk or opengl)
set RENDERER=%1

:: fallback if run directly without args
if "%RENDERER%"=="" set RENDERER=skiavk

if "%RENDERER%"=="skiavk" (
    set "RENDERER_DISPLAY=Vulkan"
) else (
    set "RENDERER_DISPLAY=OpenGL"
)

:profile_menu
:: reset colors to normal at start of menu
color 0F
cls
mode con: cols=58 lines=27
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo %CYAN%     Selected API: %RENDERER_DISPLAY% %RESET%
echo      Select GAMA's aggression level for stopping
echo      background apps after setting your preferred
echo      graphics API.
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo %CYAN%     [1] Normal (RECOMMENDED)%RESET%
echo          - Soft-restarts SystemUI, Settings, Samsung
echo          - Launcher and AOD-related processes
echo.
echo %CYAN%     [2] Aggressive%RESET%
echo          - Force-stops ALL processes (this has 
echo          downsides, press [3] to learn more)
echo.
echo %CYAN%     [X] Cancel%RESET%
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo       Enter your choice -
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%

:: handle the choice logic properly
choice /c 123X /n
set choice_sep=%ERRORLEVEL%

if %choice_sep%==4 goto exit_script
if %choice_sep%==3 call bin\aggressive_profile_learn_more.bat & goto profile_menu
if %choice_sep%==2 goto apply_aggressive
if %choice_sep%==1 goto apply_normal

:apply_normal
cls
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo %CYAN%      Applying %RENDERER_DISPLAY% ... %RESET%
echo %CYAN%      This might take a moment...%RESET%
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.

"%~dp0\adb" shell setprop debug.hwui.renderer %RENDERER%
"%~dp0\adb" shell am crash com.android.systemui
"%~dp0\adb" shell am force-stop com.android.settings
"%~dp0\adb" shell am force-stop com.sec.android.app.launcher
"%~dp0\adb" shell am force-stop com.samsung.android.app.aodservice

if %ERRORLEVEL%==0 (
    goto success_screen
) else (
    goto error_screen
)

:apply_aggressive
cls
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo %CYAN%      Applying %RENDERER_DISPLAY% ... %RESET%
echo %CYAN%      This might take a moment...%RESET%
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.

"%~dp0\adb" shell setprop debug.hwui.renderer %RENDERER%
"%~dp0\adb" shell "for a in $(pm list packages | grep -v ia.mo | cut -f2 -d:); do am force-stop \"$a\"; done" >nul 2>&1

if %ERRORLEVEL%==0 (
    goto success_screen
) else (
    goto error_screen
)

:success_screen
cls
mode con: cols=58 lines=10
color 0A
echo.
echo   = -------------------------------------------------- =
echo.
echo           Okay, %RENDERER_DISPLAY% has been applied.
echo.
echo   = -------------------------------------------------- =
echo.
pause
goto exit_script

:error_screen
color 0C
echo.
echo   = -------------------------------------------------- =
echo.
echo             Oh-oh. An error has occurred.
echo           Are you sure your phone is plugged in?
echo.
echo   = -------------------------------------------------- =
echo.
pause
goto exit_script

:exit_script
color 0F
ver > nul
goto :eof
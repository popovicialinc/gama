:: EASY CONTROLS
set RENDERER=opengl

:: ESSENTIALS
@echo off
setlocal EnableDelayedExpansion
title GAMA %version%
set CYAN=[96m
set RESET=[0m
cls

if "%RENDERER%"=="skiavk" (
    set "RENDERER_DISPLAY=Vulkan"
) else (
    set "RENDERER_DISPLAY=OpenGL"
)

mode con: cols=58 lines=26
color 0F
cls
echo.
echo %CYAN%  = -------------------------------------------------- =%RESET%
echo.
echo %CYAN%     Selected API: %RENDERER_DISPLAY% %RESET%
echo      Select GAMA's aggression level for stopping
echo      background apps
echo.
echo %CYAN%  = -------------------------------------------------- =%RESET%
echo.
echo %CYAN%      [1] Normal (RECOMMENDED) %RESET%
echo          - Forcefully terminates SystemUI, Settings,  
echo      Samsung Launcher, and AOD-related processes   
echo.
echo %CYAN%      [2] Aggressive %RESET%
echo          - Force-stop every installed app. Press [3]  
echo      to learn more.
echo.
echo %CYAN%      [X] Cancel%RESET%
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo	  Enter your choice: 
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
choice /c 123X /n >nul
set aggressive_choice=%ERRORLEVEL%
if %aggressive_choice%==1 goto :normal_profile
if %aggressive_choice%==2 goto :aggressive_profile
if %aggressive_choice%==3 (
	call bin\aggressive_profile_learn_more.bat
	goto :eof
)
if %aggressive_choice%==4 (
	goto :eof
)





:: =================== NORMAL PROFILE ===================

:normal_profile
cls
echo.
echo %CYAN%  = -------------------------------------------------- =%RESET%
echo.
echo       Applying the selected API...
echo.
echo %CYAN%  = -------------------------------------------------- =%RESET%

"%~dp0\adb" shell setprop debug.hwui.renderer %RENDERER%
"%~dp0\adb" shell am crash com.android.systemui
"%~dp0\adb" shell am force-stop com.android.settings
"%~dp0\adb" shell am force-stop com.sec.android.app.launcher
"%~dp0\adb" shell am force-stop com.samsung.android.app.aodservice
"%~dp0\adb" shell am force-stop com.google.android.inputmethod.latin
set setprop_status=%ERRORLEVEL%

if %ERRORLEVEL%==0 (
	color 0A
	echo.
	echo   = -------------------------------------------------- =
	echo.
	echo       Okay, %RENDERER_DISPLAY% has been applied.
	echo.
	echo   = -------------------------------------------------- =
	echo.
) else (
	color 0C
	echo.
	echo   = -------------------------------------------------- =
	echo.
	echo      Oh-oh. An error has occurred.
	echo      Visit GAMA's GitHub repository from the main
	echo      menu - You should find a solution there.
	echo.
	echo   = -------------------------------------------------- =
	echo.
)
pause
goto :eof

:: =================== AGGRESSIVE PROFILE ===================
:aggressive_profile
cls
echo.
echo %CYAN%  = -------------------------------------------------- =%RESET%
echo.
echo       Applying the selected API...
echo.
echo %CYAN%  = -------------------------------------------------- =%RESET%

"%~dp0\adb" shell setprop debug.hwui.renderer %RENDERER%
"%~dp0\adb" shell "for a in $(pm list packages | grep -v ia.mo | cut -f2 -d:); do am force-stop \"$a\"; done" >nul 2>&1

if %ERRORLEVEL%==0 (
	color 0A
	echo.
	echo   = -------------------------------------------------- =
	echo.
	echo       Okay, %RENDERER_DISPLAY% has been applied.
	echo.
	echo   = -------------------------------------------------- =
	echo.
) else (
	color 0C
	echo.
	echo   = -------------------------------------------------- =
	echo.
	echo      Oh-oh. An error has occurred.
	echo      Visit GAMA's GitHub repository from the main
	echo      menu - You should find a solution there.
	echo.
	echo   = -------------------------------------------------- =
	echo.
)
pause
goto :eof

@echo off
title GAMA v1.4.1
setlocal EnableDelayedExpansion
cls

set CYAN=[96m
set RESET=[0m

set RENDERER=skiavk
set RENDERER_DISPLAY=Vulkan

mode con: cols=58 lines=25
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
echo      Forcefully terminates SystemUI, Settings, Samsung 
echo      Launcher, and AOD-related processes   
echo.
echo %CYAN%      [2] Aggressive %RESET%
echo      Force-stop every installed app. Press [3] to learn
echo      more.
echo.
echo %CYAN%      [X] Cancel%RESET%
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo	  Enter your choice: 
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
choice /c 123XS /n >nul
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

if %aggressive_choice%==5 goto :shizuku_start

:: =================== START SHIZUKU ====================
:shizuku_start
mode con: cols=150 lines=35
cls
echo %CYAN%  = -------------------------------------------------- =%RESET%
echo.
echo       Starting Shizuku...
echo.
echo %CYAN%  = -------------------------------------------------- =%RESET%
adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
if %ERRORLEVEL%==0 (
	color 0A
	echo.
	echo   = -------------------------------------------------- =
	echo.
	echo       Okay, Shizuku is running.
	echo.
	echo   = -------------------------------------------------- =
	echo.
) else (
	color 0C
	echo.
	echo   = -------------------------------------------------- =
	echo.
	echo      Oh-oh. An error has occurred.
	echo.
	echo   = -------------------------------------------------- =
	echo.
)
pause
goto :eof


:: =================== NORMAL PROFILE ===================

:normal_profile
cls
echo.
echo %CYAN%  = -------------------------------------------------- =%RESET%
echo.
echo       Applying the selected API...
echo.
echo %CYAN%  = -------------------------------------------------- =%RESET%

:: PRESERVE VARIABLES
::for /f "delims=" %%A in ('adb shell settings get system accelerometer_rotation') do set "AUTO_ROT=%%A"
::for /f "delims=" %%A in ('adb shell settings get secure enabled_accessibility_services') do set "CUR_ACCESS=%%A"
::for /f "tokens=2 delims={}" %%A in ('adb shell dumpsys wallpaper ^| find "mWallpaperComponent"') do (
::  for /f "tokens=1,2 delims=/" %%P in ("%%A") do (
::    set "WALLPAPER_PKG=%%P"
::    set "WALLPAPER_SVC=%%Q"
::  )
::)
::for /f "delims=" %%A in ('adb shell settings get secure edge_enable') do set "EDGE_ENABLE=%%A"
::for /f "delims=" %%A in ('adb shell settings get secure edge_panels_enabled') do set "EDGE_PANELS=%%A"

adb shell setprop debug.hwui.renderer %RENDERER%
adb shell am crash com.android.systemui
adb shell am force-stop com.android.settings
adb shell am force-stop com.sec.android.app.launcher
adb shell am force-stop com.samsung.android.app.aodservice
adb shell am force-stop com.google.android.inputmethod.latin
set setprop_status=%ERRORLEVEL%

::rem Restore auto-rotation
::adb shell settings put system accelerometer_rotation %AUTO_ROT%
::rem Restore accessibility
::adb shell settings put secure enabled_accessibility_services "%CUR_ACCESS%"
::rem Restore wallpaper component (package/service)
::adb shell cmd wallpaper set %WALLPAPER_PKG%/%WALLPAPER_SVC% 2>nul
::rem Restore edge panels
::adb shell settings put secure edge_enable %EDGE_ENABLE%
::adb shell settings put secure edge_panels_enabled %EDGE_PANELS%

if %ERRORLEVEL%==0 (
	color 0A
	echo.
	echo   = -------------------------------------------------- =
	echo.
	echo       Done, %RENDERER_DISPLAY% has been applied.
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
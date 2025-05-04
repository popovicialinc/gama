@echo off
setlocal
cls

:main_menu
mode con: cols=56 lines=27
color 0B
cls

echo.
echo   ====================================================
echo.
echo      GPU API Manager for Android-based devices 
echo      (GAMA v1.3.2)
echo.
echo   ====================================================
echo.
echo      Hey there! What would you like the script to do?
echo.
echo      [1] Set GPU API to Vulkan, then close all apps
echo.
echo      [2] Set GPU API to OpenGL, then close all apps
echo.
echo      [3] Launch all app
echo.
echo      Additionally...
echo.
echo      [4] Visit GAMA's GitHub repository
echo.
echo      [X] Exit
echo.
echo.
echo   ===================================================
echo.

echo	 Enter your choice: 
choice /c 1234X /n >nul
goto screen%ERRORLEVEL%

:screen1
call :set_vulkan_kill_apps
goto :eof

:screen2
call :set_opengl_kill_apps
goto :eof

:screen3
call :launch_all_apps
goto :eof

:screen4
start https://github.com/popovicialinc/gama
goto :main_menu

:screen5
exit



:set_vulkan_kill_apps
mode con: cols=90 lines=30
color 0F
cls
echo.
echo   ====================================================================================
echo.
echo      How aggressive should GAMA be when stopping apps?
echo.
echo   ====================================================================================
echo.
echo      [1] - Normal - Induce SystemUI, Settings, Samsung Launcher, AOD Services crash
echo      [2] - Aggressive - Force-stop ALL apps  
echo      (guarantees system-wide use of Vulkan, but it takes a while to execute)
echo.
choice /c 12 /n >nul
set aggressive_choice=%ERRORLEVEL%

if %aggressive_choice%==1 goto aggressive_vulkan_1
if %aggressive_choice%==2 goto aggressive_vulkan_2

goto :main_menu


:aggressive_vulkan_1
	adb shell setprop debug.hwui.renderer skiavk
	adb shell am crash com.android.systemui
	adb shell am force-stop com.android.settings
	adb shell am force-stop com.sec.android.app.launcher
	adb shell am force-stop com.samsung.android.app.aodservice
	adb shell am crash com.google.android.inputmethod.latin b

	if %ERRORLEVEL%==0 (
		color 0A
		echo      Vulkan has been set.
		echo.
	) else (
		color 0C
		echo      Something went wrong. Is your device connected?
		echo.
	)
	pause
	goto :main_menu


:aggressive_vulkan_2
	adb shell setprop debug.hwui.renderer skiavk
	adb shell "for a in $(pm list packages | grep -v ia.mo | cut -f2 -d:); do am force-stop \"$a\"; done" >nul 2>&1

	if %ERRORLEVEL%==0 (
		color 0A
		echo      Vulkan has been set.
		echo.
	) else (
		color 0C
		echo      Something went wrong. Is your device connected?
		echo.
	)
	pause
	goto :main_menu





:set_opengl_kill_apps
mode con: cols=90 lines=30
color 0F
cls
echo.
echo   ====================================================================================
echo.
echo      How aggressive should GAMA be when stopping apps?
echo.
echo   ====================================================================================
echo.
echo      [1] - Normal - Induce SystemUI, Settings, Samsung Launcher, AOD Services crash
echo      [2] - Aggressive - Force-stop ALL apps  
echo      (guarantees system-wide use of Vulkan, but it takes a while to execute)
echo.
choice /c 12 /n >nul
set aggressive_choice=%ERRORLEVEL%

if %aggressive_choice%==1 goto aggressive_opengl_1
if %aggressive_choice%==2 goto aggressive_opengl_2

goto :main_menu


:aggressive_opengl_1
	adb shell setprop debug.hwui.renderer opengl
	adb shell am crash com.android.systemui
	adb shell am force-stop com.android.settings
	adb shell am force-stop com.sec.android.app.launcher
	adb shell am force-stop com.samsung.android.app.aodservice
	adb shell am crash com.google.android.inputmethod.latin b

	if %ERRORLEVEL%==0 (
		color 0A
		echo      OpenGL has been set.
		echo.
	) else (
		color 0C
		echo.
		echo      Something went wrong. Is your device connected?\
		echo.
	)
	pause
	goto :main_menu


:aggressive_opengl_2
	adb shell setprop debug.hwui.renderer opengl
	adb shell "for a in $(pm list packages | grep -v ia.mo | cut -f2 -d:); do am force-stop \"$a\"; done" >nul 2>&1

	if %ERRORLEVEL%==0 (
		color 0A
		echo      OpenGL has been set.
		echo.
	) else (
		color 0C
		echo.
		echo      Something went wrong. Is your device connected?
		echo.
	)
	pause
	goto :main_menu










:launch_all_apps
mode con: cols=89 lines=30
color 0F
cls
echo.
echo    ====================================================================================
echo.
echo      WARNING: Launching all apps may heat up your device! It may also take a while...
echo.
echo    ====================================================================================
echo.
echo.     Do you REALLY want to launch all apps? [Y/N]
choice /c YN >nul
goto option%ERRORLEVEL%

:option1
call :launch_apps

:option2
call :main_menu

:launch_apps
color 0F
cls
echo.
echo      Launching all apps...
echo.

adb shell "for pkg in $(pm list packages | cut -f2 -d:); do monkey -p "$pkg" -c android.intent.category.LAUNCHER 1; done" 2>&1 | findstr /v "** No activities found to run"
if %ERRORLEVEL% neq 0 (
	color 0C
	echo      Something went wrong. Is your device connected?
	echo.
) else (
    color 0A
    echo All apps launched successfully!
)
pause
goto :main_menu


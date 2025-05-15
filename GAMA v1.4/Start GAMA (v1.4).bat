@echo off
setlocal EnableDelayedExpansion
cls

:main_menu
set adb_status=Disconnected
for /f "tokens=1" %%A in ('adb get-state 2^>nul') do (
    if "%%A"=="device" (
        set adb_status=Connected
    )
)

mode con: cols=58 lines=26
color 0B
cls

echo.
echo   ======================================================
echo.
echo      GPU API Manager for Android-based devices
echo      (GAMA v1.4) // Released 15.05.2025
echo      Device status - !adb_status!
echo.
echo   ======================================================
echo.
echo      Hi. What would you like GAMA to do for you?
echo.
echo      Core functions
echo      [1] Switch to Vulkan graphics API
echo      [2] Switch to OpenGL graphics API
echo      [3] Launch all apps
echo.
echo      Additionally...
echo      [4] Visit GAMA's GitHub repository
echo      [5] Check for device connection
echo      [X] Exit
echo.
echo   ======================================================
echo.

echo	 Enter your choice: 
choice /c 12345X /n >nul
goto screen%ERRORLEVEL%

:screen1
call :set_renderer_with_aggression skiavk

:screen2
call :set_renderer_with_aggression opengl

:screen3
call :launch_all_apps

:screen4
start https://github.com/popovicialinc/gama
goto :main_menu

:screen5
goto :main_menu

:screen6
exit




:set_renderer_with_aggression
set RENDERER=%1
set "RENDERER_DISPLAY=%RENDERER%"
if "%RENDERER%"=="skiavk" set "RENDERER_DISPLAY=Vulkan"
if "%RENDERER%"=="opengl" set "RENDERER_DISPLAY=OpenGL"

mode con: cols=85 lines=17
color 0F
cls
echo.
echo   =================================================================================
echo.
echo      Select GAMA's aggression level for stopping background apps (%RENDERER_DISPLAY%)
echo.
echo   =================================================================================
echo.
echo      [1] Normal (RECOMMENDED)
echo      Forcefully terminates SystemUI, Settings, Samsung Launcher, and AOD-related 
echo      processes
echo.
echo      [2] Aggressive
echo      Force-stop every installed app. Press [3] to learn more.
echo.
echo      [X] Cancel
echo.
choice /c 123X /n >nul
set aggressive_choice=%ERRORLEVEL%

if %aggressive_choice%==1 goto :apply_normal_renderer
if %aggressive_choice%==2 goto :apply_aggressive_renderer
if %aggressive_choice%==3 goto :aggressive_learn_more
if %aggressive_choice%==4 goto :main_menu




:apply_normal_renderer
cls
adb shell setprop debug.hwui.renderer %RENDERER%
adb shell am crash com.android.systemui
adb shell am force-stop com.android.settings
adb shell am force-stop com.sec.android.app.launcher
adb shell am force-stop com.samsung.android.app.aodservice
adb shell am crash com.google.android.inputmethod.latin b

if %ERRORLEVEL%==0 (
	color 0A
	echo.
	echo   =================================================================================
	echo.
	echo      %RENDERER_DISPLAY% has been successfully set.
	echo.
	echo   =================================================================================
	echo.
) else (
	color 0C
	echo.
	echo   =================================================================================
	echo.
	echo      Oh-oh. An error has occured.
	echo.
	echo   =================================================================================
	echo.
)
pause
goto :main_menu




:apply_aggressive_renderer
cls
adb shell setprop debug.hwui.renderer %RENDERER%
adb shell "for a in $(pm list packages | grep -v ia.mo | cut -f2 -d:); do am force-stop \"$a\"; done" >nul 2>&1

if %ERRORLEVEL%==0 (
	color 0A
	echo.
	echo   =================================================================================
	echo.
	echo      %RENDERER_DISPLAY% has been successfully set.
	echo.
	echo   =================================================================================
	echo.
) else (
	color 0C
	echo.
	echo   =================================================================================
	echo.
	echo      Oh-oh. An error has occured.
	echo.
	echo   =================================================================================
	echo.
)
pause
goto :main_menu




:aggressive_learn_more
cls
mode con: cols=85 lines=23
echo.
echo   =================================================================================
echo.
echo      About the "Aggressive" Profile
echo.
echo   =================================================================================
echo.
echo      This profile scans all installed packages on your Android device and tries to 
echo      force-stop everything - yes, even the ones not currently running. The goal? 
echo      To make sure nearly every app is forced to use the selected graphics API.
echo.
echo      But heads-up, this method is brutal. Potential side effects include:
echo      - Breaking WiFi Calling / VoLTE
echo      - Resetting your default browser and keyboard
echo      - Some apps just refusing to run under the selected API
echo      - Possibly other weirdness we haven't documented yet
echo.
echo      Unless you know what you're doing, stick with the "Normal" profile. 
echo.
echo      Would you like to visit GAMA's GitHub repository to learn more? (Y/N)
echo      (Either way, you'll be sent back to the previous screen)
echo.
choice /c YN /n >nul
set aggressive_learn_more_choice=%ERRORLEVEL%

if %aggressive_learn_more_choice%==1 start https://github.com/popovicialinc/gama?tab=readme-ov-file#%EF%B8%8Fknown-issues
goto :set_renderer_with_aggression %RENDERER%

:launch_all_apps
mode con: cols=89 lines=30

color 0F
cls
echo.
echo   ====================================================================================
echo.
echo      WARNING: Launching all apps will heat up your device! It may also take a while...
echo.
echo   ====================================================================================
echo.
echo      Are you certain whatever you're doing is worth it? [Y/N]
echo.
choice /c YN >nul
goto option%ERRORLEVEL%

:option1
call :launch_apps

:option2
goto :main_menu

:launch_apps
color 0F
cls
echo.
echo      Launching all apps...

adb shell "for pkg in $(pm list packages | cut -f2 -d:); do monkey -p \"$pkg\" -c android.intent.category.LAUNCHER 1; done" 2>&1 | findstr /v "** No activities found to run"
if %ERRORLEVEL% neq 0 (
	color 0C
	echo.
	echo   =================================================================================
	echo.
	echo      Oh-oh. An error has occured.
	echo.
	echo   =================================================================================
	echo.
) else (
	color 0A
	echo.
	echo   =================================================================================
	echo.
	echo      All apps launched successfully! 
	echo      Clear them using the Recents menu - "Close all".
	echo.
	echo   =================================================================================
	echo.
)
pause
goto :main_menu
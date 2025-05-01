@echo off
cls
echo ----------------------------------------
echo Welcome to the ADB Automation Script!
echo This script will perform the following steps:
echo 1. Set the renderer to "skiavk"
echo 2. Force stop all apps (except those with 'ia.mo')
echo 3. Launch all apps using the "monkey" tool
echo ----------------------------------------

pause
echo.

:: Step 1: Set renderer to skiavk
echo Step 1: Setting renderer to skiavk...
adb shell "setprop debug.hwui.renderer skiavk"
echo Renderer set to skiavk.
echo Press any key to continue to the next step...
pause
echo.

:: Step 2: Force stop all apps (except those with 'ia.mo')
echo Step 2: Force stopping all apps (except 'ia.mo' packages)...
adb shell "for a in $(pm list packages | grep -v ia.mo | cut -f2 -d:); do am force-stop \"$a\" & done > /dev/null 2>&1 &"
echo All apps (except 'ia.mo' packages) have been force-stopped.
echo Press any key to continue to the next step...
pause
echo.

:: Step 3: Launch all apps using the monkey tool
echo Step 3: Launching all apps using the monkey tool...
adb shell "for pkg in $(pm list packages | cut -f2 -d:); do monkey -p \"$pkg\" -c android.intent.category.LAUNCHER 1; done"
echo All apps have been launched using the monkey tool.
echo Script completed!
pause
exit

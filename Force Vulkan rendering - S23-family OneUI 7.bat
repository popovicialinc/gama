@echo off
cls
echo  = ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ =
echo  = Hey there! This script will:                          =
echo  = Force Vulkan rendering & force-stop all apps          =
echo  =                                                       =
echo  = (Optional) Launch all apps                            =
echo  = ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ =

echo.
pause
echo.

echo Step 1: Setting renderer to Vulkan & force-stopping all apps...
adb shell "setprop debug.hwui.renderer skiavk"
adb shell "for a in $(pm list packages | grep -v ia.mo | cut -f2 -d:); do am force-stop \"$a\" & done > /dev/null 2>&1 &"

echo The script is done! You can now safely close this instance.

echo.

echo If you want to launch all apps . . . 

pause
echo.

echo Your device will get quite warm.
echo Are you ABSOLUTELY sure you want to launch ALL apps?
pause

echo Step 3: Launching all apps...
adb shell "for pkg in $(pm list packages | cut -f2 -d:); do monkey -p \"$pkg\" -c android.intent.category.LAUNCHER 1; done"
echo All apps have been launched!
echo You should close all apps in the Recents menu.
echo Script completed!
pause
exit

@echo off
cls
echo  = ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ =
echo  = Hey there! This script will:                          =
echo  = Set the renderer to Vulkan and force-stop all apps    =
echo  =                                                       =
echo  = (Optional) Relaunch all apps                          =
echo  = ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ =

echo.
pause
echo.

echo Step 1: Setting renderer to "skiavk" (Vulkan) and force-stopping all apps...
adb shell "setprop debug.hwui.renderer skiavk"
adb shell "for a in $(pm list packages | grep -v ia.mo | cut -f2 -d:); do am force-stop \"$a\" & done > /dev/null 2>&1 &"
cls

echo The script is done! You can now safely close this instance.

echo.

echo If you want to relaunch all apps...

pause
echo.

echo Your device will get hot, but don't worry, it's normal! 
echo Are you sure you want to relaunch ALL apps?
pause

echo Step 3: Launching all apps...
adb shell "for pkg in $(pm list packages | cut -f2 -d:); do monkey -p \"$pkg\" -c android.intent.category.LAUNCHER 1; done"
echo All apps have been relaunched!
echo You should close all apps in the Recents menu.
echo Script completed!
pause
exit

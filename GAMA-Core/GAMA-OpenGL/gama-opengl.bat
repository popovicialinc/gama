@echo off
"%~dp0\adb" shell setprop debug.hwui.renderer opengl
"%~dp0\adb" shell am crash com.android.systemui
"%~dp0\adb" shell am force-stop com.android.settings
"%~dp0\adb" shell am force-stop com.sec.android.app.launcher
"%~dp0\adb" shell am force-stop com.samsung.android.app.aodservice
"%~dp0\adb" shell am crash com.google.android.inputmethod.latin b
@echo off
adb shell setprop debug.hwui.renderer opengl
adb shell am crash com.android.systemui
adb shell am force-stop com.android.settings
adb shell am force-stop com.sec.android.app.launcher
adb shell am force-stop com.samsung.android.app.aodservice
adb shell am crash com.google.android.inputmethod.latin b
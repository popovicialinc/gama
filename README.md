**✅ What It Does**

This .bat script forces your Samsung Galaxy S23, S23+, or S23 Ultra to use Vulkan instead of OpenGL for GPU rendering — no root required. Ideal for those who have updated to OneUI 7 and suffer from high temperatures (and thus worse performance, often accompanied by slower charging) and poor battery life.

**🧩 Requirements**

• A computer that runs Windows.

• SDK Platform Tools. [Download](https://dl.google.com/android/repository/platform-tools-latest-windows.zip).

• The script itself. [Download](https://github.com/popovicialinc/s23_oneui7_vulkan/releases/latest).

**📦 Installation & Usage**

Okay, now that you have gathered all the essential, we have to set it all up.

**The simple way**

1) Extract "platform-tools-latest-windows.zip"
   
2) Copy the .bat file in the "platform-tools" folder

3) Run the script

**The permanent & difficult but recommended way**

1) Extract "platform-tools-latest-windows.zip" to a safe, permanent location — somewhere you won’t accidentally delete or move it later.
   
2) Within the "platform-tools" folder, search for "adb.exe", right-click it and select "Copy as path". This will be useful in just a moment.
   
3) Search for "Edit the system environment variables"
   
4) In the bottom-right corner, you should see "Environment Variables...'; click on it.
  
5) Now, you're faced with two section: "User variables" (on the top) and "System variables" (on the bottom).
  
6) In "System variables", you should see a table with the headers "Variable" and "Value". On the first column (whose header is "Variable"), locate "Path" and double-click on it.
  
7) Now, pick any blank spot (where there isn't ANY text whatsoever; make sure NOT to mess with anything that's already there, you could break something!), double-click on that blank spot, and paste the path of "adb.exe" (see step 2 if you haven't copied its path already)
    
8) To test if you have done the steps correctly, search for CMD (Command Prompt), and type "adb". If a bunch of text appears, you're golden; if not, re-do the previous steps)

9) If you have succesfully followed each steps and confirmed everything is working fine, you are good to go!

10) Keep pressing "OK" until all panels are closed.

FORCING VULKAN RENDERING

You've done great setting up ADB, excellent job! Should you intersect with ADB again in the future, you won't be chained to the platform-tools folder!

1) Locate the "Force.Vulkan.S23.S23+.S23.Ultra.-.OneUI.7.bat" script (should be in your Downloads), and double-click on it.

2) The script will instruct you further.

**✨ FREQUENTLY ASKED QUESTIONS**

**Q) Why should I even bother doing all of this?**

A) Vulkan is newer, much more efficient low-level API than OpenGL, the current API used for rendering. Samsung enabled Vulkan rendering by default on OneUI 7 Beta 1, which offered low temperatures and excellent battery life, but then disabled it in Beta 2, which resulted in higher temperatures (worse performance & possibly slower charging due to heat buildup in the device whilst using) and poor battery life. This has been noticed by many, and forcing Vulkan rendering has fixed all of that.

**Q) Is this script safe to run?**

A) Yes! It's perfectly fine and there literally is nothing you can mess up!

**Q) Do I need to run this script every time I reboot my phone?**

A) Unfortunately, yes, you do. [This excellent Reddit post](https://www.reddit.com/r/GalaxyS23Ultra/comments/1kbisga/full_tutorial_enable_vulkan_on_s23u_without_pc/) can guide you on how you can force Vulkan rendering without needing a PC at all - It's an integral solution that has the same outcome as my solution - It's a bit more complex to use and set up. A great solution nonetheless.

**Q) Why would I want to launch all apps?**

A) This script force-closes all apps so that when they restart, they'll run under Vulkan. There may be some apps that simply won't start again unless you reboot your device. Solution? The exact antithesis of "Force-close all apps": FORCE-LAUNCH ALL APPS! Your device will get quite warm, sure, but don't worry, it's (probably) gonna be fine! (👍)

**Q) Does the device need to be connected via USB to PC before starting the .bat script?**

A) Currently, the script isn't _very_ complex, it doesn't "wait" or "check" for certain conditions. For example, if no device is connected, and you press any key, ADB will throw the "error: no devices/emulators found" error, which means nothing has happened. You should probably restart the script at this point in time, because if you plug your device in RIGHT NOW and press any key, ADB will tell your device to launch EVERY. SINGLE. APP. YOU. HAVE. You would have initiated the (OPTIONAL) step (see the previous question). I'll fix this soon by adding more conditions and complexity, though! (👍👍👍)

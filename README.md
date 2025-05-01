**✅ What It Does**

This .bat script forces your Samsung Galaxy S23, S23+, or S23 Ultra to use Vulkan instead of OpenGL for GPU rendering — no root required. Ideal for those who have updated to OneUI 7.

This will provide you with better performance and better battery life.



**🧩 Requirements**

• A computer that runs Windows.

• SDK Platform Tools. [Download](https://dl.google.com/android/repository/platform-tools-latest-windows.zip).

• The script itself. [Download](https://github.com/popovicialinc/s23_oneui7_vulkan/releases/download/v1.0.0/Force.Vulkan.S23.S23+.S23.Ultra.-.OneUI.7.bat).




**📦 Installation & Usage**

Okay, now that you have gathered all the essential, we have to set it all up.


**SETTING UP ADB.EXE (if you haven't already)**

1) Extract "platform-tools-latest-windows.zip" to a safe, permanent location — somewhere you won’t accidentally delete or move it later.
   
2) Within the just-extracted folder, search for "adb.exe", right-click it and select "Copy as path". This will be useful in just a moment.
   
3) Search for "Edit the system environment variables"
   
4) In the bottom-right corner, you should see "Environment Variables...'; click on it.
  
5) Now, you're faced with two section: "User variables" (on the top) and "System variables" (on the bottom).
  
6) In "System variables", you should see a table with the headers "Variable" and "Value". On the first column (whose header is "Variable"), locate "Path" and double-click on it.
  
7) Now, pick any blank spot (where there isn't ANY text whatsoever; make sure NOT to mess with anything that's already there, you could break something!), double-click on that blank spot, and paste the path of "adb.exe" (see step 2 if you haven't copied its path already)
    
8) To test if you have done the steps correctly, search for CMD (Command Prompt), and type "adb". If a bunch of text appears, you're golden; if not, re-do the previous steps)

9) If you have succesfully followed each steps and confirmed everything is working fine, you are good to go!

10) Keep pressing "OK" until all panels are closed.



**FORCING VULKAN RENDERING**

You've done great in setting up ADB, excellent job!

1) Locate the "Force.Vulkan.S23.S23+.S23.Ultra.-.OneUI.7.bat" script (should be in your Downloads), and double-click on it.

2) The script will instruct you further.

**✨ FREQUENTLY ASKED QUESTIONS**

**Q) Why should I even bother doing all of this?**

A) Vulkan is newer, much more efficient low-level API than OpenGL, the current API used for rendering. Samsung enabled Vulkan rendering by default on OneUI 7 Beta 1, which offered low temperatures and strong battery life, but then disabled it in Beta 2, which resulted in higher temperatures (charging might be slower or stopped if the device is too) and poor battery life. This has been noticed by many.

**Q) Is this script safe to run?**

A) Yes! It's perfectly fine and there literally is nothing you can mess up!

**Q) Do I need to run this script every time I reboot my phone?**

A) Unfortunately, yes, you do. But don't worry, if you've set up ADB, all you really need to do is to use the script again.'

Q) Why should I relaunch all apps?

A) This script kills all apps so they'll re-start and run using Vulkan instead of OpenGL. If there any apps that won't start on their own until the next reboot, you should probably relaunch all apps. Your device will get quite warm, but don't worry, it's expected.

Oh, and on the next reboot, the default will not be Vulkan, it will be OpenGL, so you'll need to re-run the script.

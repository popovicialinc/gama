#✅ Overview

GAMA is a Windows batch script that redirects your Samsung device’s GPU renderer from OpenGL to Vulkan—no root needed. It’s tailor-made for One UI 7 users battling overheating, throttled performance, and suboptimal battery life.

Shoutout: This project is inspired by Ameen Sha Cheeranga’s Linux script. Much love! ❤️

#🧩 Prerequisites

* a Windows PC

* [SDK Platform Tools](https://dl.google.com/android/repository/platform-tools-latest-windows.zip)

* [GAMA Script](https://github.com/popovicialinc/gama/releases/latest)

#📦 Installation & Usage

## Quick Start (Temporary)

* Extract platform-tools-latest-windows.zip.

* Copy Force.Vulkan.rendering.on.S23-family.OneUI.7.bat into the platform-tools folder.

* Double‑click the .bat file and follow the prompts.

* Heads up: You’ll need to rerun this script after every phone reboot.

## Recommended Setup (Permanent)

### Extract & Store

* Unzip platform-tools-latest-windows.zip into a safe, permanent folder.

* Add ADB to Your PATH

* In your platform-tools folder, shift+right-click adb.exe → Copy as path.

* Open Edit the system environment variables.

* Click Environment Variables....

* Under System variables, find and edit Path.

* Add a new entry—paste the adb.exe path.

* Press OK until all dialogs close.

* Verify

* Open Command Prompt, type adb. If you see ADB help text, congrats  —you’re set!

* Run GAMA

* Follow the on‑screen instructions.

### Pro Tip: Once ADB is in your PATH, you can invoke it from anywhere — you're not chained to the platforms-tools folder anymore.

#✨ FAQ

**Q: Why bother with Vulkan?** A: Vulkan is a newer, low‑overhead graphics API. One UI 7 Beta 1 defaulted to Vulkan, which kept temperatures cool and battery life strong. Beta 2 reverted to OpenGL—enter overheating and drain. This script forces Vulkan back on.

**Q: Is it safe?** A: Absolutely! It leverages official ADB commands—no system hacks, no risk.

**Q: Must I run it after every reboot?** A: Yes, but check out this Reddit tutorial for a PC‑free, one‑and‑done method.

**Q: Why does it close all apps?** A: Apps must relaunch under Vulkan. Some may insist on a reboot—just rerun the script if needed.

**Q: Do I need a USB connection?** A: Nope. Plug in before or after launching—the script will detect your device when you hit Enter.

# ✅Overview

**GAMA (GPU API Manager for Android-based devices) is a Windows batch script that changes your Android device’s GPU API — no root needed.**

It’s tailor-made for One UI 7 users battling overheating, throttled performance, and suboptimal battery life, especially S23 users - Forcing Vulkan rendering is a great way to enhance performance, increase battery life and lower running temperatures.

[**The unofficial Linux equivalent of GAMA**](https://github.com/Ameen-Sha-Cheerangan/s23-ultra-vulkan-linux-script)

# 🧩Prerequisites
* **A Windows PC**
* **Your Android device** ([with USB Debugging enabled](https://github.com/popovicialinc/gama/blob/main/README.md#to-enable-usb-debugging))
* **The latest version of** [**GAMA**](https://github.com/popovicialinc/gama/releases/latest)


# 📦Installation & Usage
* Extract the GAMA archive
* Connect your device via USB (make sure USB Debugging is enabled, otherwise GAMA won't recognise your device)
* Double-click "gama-core.bat"

## To enable USB Debugging
* Settings > About phone > Software information > Press 7 times on Build number, input your password and a new "Developer options" button will appear at the very bottom of the Settings app
* Developer options > Scroll down and find "USB Debugging"; enable it and you're ready to race!

**Heads up: You’ll need to run this script after every phone reboot.**

# ⚠️Known issues (list may expand in the future)
## Caused by Vulkan
* **Visual artifacting**. Adreno GPUs aboard Snapdragon SOCs usually have no problems with Vulkan, so your mileage may vary - though my personal experience with Vulkan has been great on my S23 Ultra.
* **Not all apps will run under Vulkan**. The majority will, but there will be some exceptions. Nothing can fix this - Samsung needs to adopt Vulkan on all of their models, and the app developers need to fix their apps.

## Caused by system-wide app restart (aggressive app stop; solvable by using the more leanient "Normal" profile when GAMA asks you how aggressive it should be when stopping apps)
* **The default browser and the default keyboard will get reset.**
* **Loss of WiFi-Calling/VoLTE capability** - **Fix**: Go to Settings > Connections > SIM manager, then toggle SIM 1/2 off and back on. (many thanks to Fun-Flight4427 and ActualMountain7899 for reporting the bug and finding a solution)

# ✨Frequently asked questions

**Q: Why bother with Vulkan?**
A: Vulkan is a newer, low‑overhead graphics API. One UI 7 Beta 1 defaulted to Vulkan, which kept temperatures cool and battery life strong. Beta 2 reverted to OpenGL — enter overheating and drain. This script forces Vulkan back on.

**Q: Is it safe?**
A: Absolutely! It leverages official ADB commands — no system hacks, no risk.

**Q: Must I run it after every reboot?**
A: Yes, unfortunately.

**Q: How do I pronounce "GAMA"?**
A: It's pronounced exactly the same as "gamma".

**Q: Will this void my warranty or trip Knox?**
A: Nope! This doesn’t mess with bootloaders or root. It’s 100% Knox-safe and warranty-friendly.

**Q: Will it break my phone?**
A: It’s just a graphics driver toggle - your device will be just fine.

**Q: What if I don’t notice a difference?**
A: Some devices and apps won’t show a huge change, but Vulkan generally offers better performance and thermal management. If you’re not seeing improvement, your phone might already be optimized.

**Q: Can I undo this?**
A: Yup. GAMA now natively supports back-and-forth API changing - OpenGL-to-Vulkan or Vulkan-to-OpenGL. Easy peasy. Besides, a reboot wIll also reset the driver back to OpenGL, so you've got plenty of options!

**Q: Why isn’t this a built-in setting already?!**
A: Great question — ask Samsung. If you ask me, this smells a whole lot like planned obsolescence to me... but anyway.

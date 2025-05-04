# ✅Overview

GAMA (GPU API Manager for Android-based devices) is a Windows batch script that changes your Android device’s GPU API  — no root needed. 

It’s tailor-made for One UI 7 users battling overheating, throttled performance, and suboptimal battery life, especially S23 users - Forcing Vulkan rendering is a great way to enhance performance, increase battery life and lower running temperatures.

[**The Linux equivalent of GAMA**](https://github.com/Ameen-Sha-Cheerangan/s23-ultra-vulkan-linux-script)

# 🧩Prerequisites
* A Windows PC
* The latest version of [GAMA](https://github.com/popovicialinc/gama/releases/latest)

# 📦Installation & Usage
* Extract the GAMA archive
* Run "GAMA.bat"

**Heads up: You’ll need to run this script after every phone reboot.**

# ⚠️Known issues (list may expand in the future)
## Caused by Vulkan
* **Visual artifacting**. Adreno GPUs aboard Snapdragon SOCs usually have no problems with Vulkan, so your mileage may vary - though my personal experience with Vulkan has been great on my S23 Ultra.

## Caused by system-wide app restart (aggressive app stop)
* **The default browser and the default keyboard will get reset.**
* **Loss of WiFi-Calling/VoLTE capability** - **Fix**: Go to Settings > Connections > SIM manager, then toggle SIM 1/2 off and back on. (many thanks to Fun-Flight4427 and ActualMountain7899 for reporting the bug and finding a solution)

# ✨FAQ

* **Q: Why bother with Vulkan?** A: Vulkan is a newer, low‑overhead graphics API. One UI 7 Beta 1 defaulted to Vulkan, which kept temperatures cool and battery life strong. Beta 2 reverted to OpenGL — enter overheating and drain. This script forces Vulkan back on.
* **Q: Is it safe?** A: Absolutely! It leverages official ADB commands — no system hacks, no risk.
* **Q: Must I run it after every reboot?** A: Yes, but if you're interested in a PC-free solution, check out [this Reddit tutorial](https://www.reddit.com/r/GalaxyS23Ultra/comments/1kdsmks/comment/mqdq7o3/?context=3).
* **Q: How do I pronounce "GAMA"?** A: It's pronounced exactly as "gamma".

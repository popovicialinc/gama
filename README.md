<p align="center">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/gama_title_video.gif" width="100%" alt="GAMA Ultra 4K Banner">
</p>

<br>

<br>

# **Overview**
<p align="left">
  <a href="https://www.android.com/"><img src="https://img.shields.io/badge/Android-FFB3BA?style=for-the-badge&logo=android&logoColor=333" /></a>&nbsp;<a href="https://news.samsung.com/global/galaxy-s23-series"><img src="https://img.shields.io/badge/Galaxy_S23-FFDFBA?style=for-the-badge&logo=samsung&logoColor=333" /></a>&nbsp;<a href="https://www.samsung.com/us/support/answer/ANS10004612/"><img src="https://img.shields.io/badge/One_UI_7-FFFFBA?style=for-the-badge&logoColor=333" /></a>&nbsp;<a href="https://github.com/palincat/gama"><img src="https://img.shields.io/badge/Safe-BAFFC9?style=for-the-badge&logoColor=333" /></a>&nbsp;<a href="https://discord.gg/Kh8ajAVUGx"><img src="https://img.shields.io/badge/Discord-BAE1FF?style=for-the-badge&logo=discord&logoColor=333" /></a>&nbsp;<a href="https://github.com/popovicialinc/gama/releases"><img src="https://img.shields.io/github/downloads/popovicialinc/gama/total?style=for-the-badge&label=Downloads&color=E8BAFF&labelColor=C98FE8&logoColor=000" /></a>
</p>

**Graphics API Manager for Android (GAMA) is an application that lets you switch the GPU rendering API on your Android device without requiring root access**

While optimized for the Samsung Galaxy S23 lineup, this project is compatible with any modern Android device and aims to provide:

* ❄️ **Lower-running temps**
* 🔋 **An improved battery life**
* 🔓 **Zero risk** - Root is not needed; 100% Knox-safe!
* 🛠️ **User-friendliness** - Simple yet beautiful UI to switch APIs without complex terminal commands

**Extra resources**
* 🛜 [**The GAMA Discord Server**](https://discord.gg/Kh8ajAVUGx)
* 📱 [**Does Vulkan work on my device?**](https://docs.google.com/spreadsheets/d/1X_UuSJBWc9O2Q9nW0x-V_WC0uY-yKDfNRkxgko8i6AA/edit?usp=sharing)
    * Vulkan support can vary between Android devices and One UI versions, so performance may differ, especially on versions below One UI 7 (Android 15). You’re always welcome to give it a try though! If something doesn’t work as expected, a quick reboot will bring everything back to normal.
    * If your device is not listed, please kindly follow [this link](https://forms.gle/qYUHHhaQNLiY9i1MA) where you will be able to fill out a form - The relevant, collected data will be added to the [spreadsheet](https://docs.google.com/spreadsheets/d/1X_UuSJBWc9O2Q9nW0x-V_WC0uY-yKDfNRkxgko8i6AA/edit?usp=sharing)
* ☕ If you want to support the development of GAMA, [**consider donating**](https://buymeacoffee.com/popovicialinc)!

<br>

<br>

# **Get started on your platform**

* 🤖 [**Android**](https://github.com/popovicialinc/gama?tab=readme-ov-file#gama-on-android)
* 🖥️ [**Windows**](https://github.com/popovicialinc/gama_windows?tab=readme-ov-file#gama-on-windows-batch)
* 🐧 [**Linux**](https://github.com/Ameen-Sha-Cheerangan/s23-vulkan-support) (adapted by Ameen Sha Cheerangan)

<br>

<br>

# **GAMA on Android**

## **Prerequisites**

* **Your Android device**
* [**The latest .apk of GAMA**](https://github.com/popovicialinc/gama/releases/latest)
* [**Shizuku**](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)
  * Alternatively, download straight from [Shizuku's official GitHub repository](https://github.com/RikkaApps/Shizuku/releases/latest)

## **Installation & Usage**

* Install **Shizuku** on your device and [*start the service*](https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging)
* Install the latest **GAMA** APK on your device.
* Open **GAMA**. The app will detect **Shizuku** and **request permission** to run commands - **grant permission**.
  * If **GAMA** displays "**Permission needed**⚠️"
    * Open **Shizuku** and select the **second option** from the top labeled *Authorized Applications*
    * Make sure the toggle next to **GAMA** is turned on.
    * *Clear **GAMA** from your Recents menu* so the app can refresh and check for permissions again.
* Click on either:
  * **Vulkan** - Lower temps, Better battery
  * **OpenGL** - Fallback option

**Heads up: You’ll need to run this script after every phone reboot.**

**Note**: Vulkan might not run perfectly on every Android device due to the wide variety of hardware and Android skins, but it’s worth giving it a try! If your phone freezes or shows a black screen, just hold VOLUME DOWN + POWER to force a restart. GAMA forces Vulkan rendering using the setprop command (shell setprop debug.hwui.renderer). This change is temporary; Vulkan will only stay active until your next reboot. Simply restarting your device will revert any changes made by GAMA!

### Automation via Tasker

* GAMA supports automation via Tasker. See the [setup guide](https://github.com/popovicialinc/gama/blob/main/GAMA%20for%20Android/GAMA_Tasker_Guide.pdf) to get started.

## **Photos**
<p align="center">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/0.png" width="759">
</p>

<p align="center">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/1.png" width="250">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/2.png" width="250">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/3.png" width="250">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/4.png" width="250">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/11.png" width="250">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/12.png" width="250">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/5.png" width="250">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/6.png" width="250">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/7.png" width="250">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/8.png" width="250">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/9.png" width="250">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/main/!assets/Repository%20Stuff/10.png" width="250">
</p>

<br>

<br>

# Caveats

<details>
<summary><b>🔸 Some apps won't run under Vulkan</b></summary>
<br>
A great majority of apps installed on your device will run under Vulkan flawlessly. If an app reverts to OpenGL, it's normal behavior and there's nothing to worry about — the app simply doesn't like Vulkan, and it probably also runs under OpenGL on S24/S25/S26-series!
<br>
<br>
</details>

<details>
<summary><b>🔸 Good Lock modules get deactivated (sometimes)</b></summary>
<br>
If OneUI detects that System UI has crashed multiple times in a short period, it may assume that a Good Lock module is responsible. Since these modules integrate deeply with System UI, OneUI may automatically disable all Good Lock modules to prevent what it believes to be a continuous crash loop.

In most cases, however, the Good Lock modules themselves are not actually the cause if you're using GAMA. The issue is usually triggered when the user switched APIs repeatedly in quick succession, which causes System UI to soft-crash several times (GAMA needs to soft-crash System UI in order to successfully apply the selected API).

To avoid this, try not to change APIs too quickly one after another.

If OneUI has already disabled your Good Lock modules, simply re-enable them in the Good Lock app and restart your device. After the reboot, the previously disabled modules should function normally! You can now apply Vulkan via GAMA, but please, do it cautiously.
<br>
<br>
</details>

<details>
<summary><b>🔸 The Aggressive Profile</b></summary>
<br>
Using the **Aggressive** profile for stopping background apps is, dare I say, nuclear, and you probably shouldn't use it... but if you **do** plan on using it, please be aware of the side effects:

- **Resets Defaults** - Your default browser and keyboard will be reset.  
  - There's nothing we can do to fix it. You will need to manually set everything back up. Sorry!

- **Connectivity Loss** - Possible loss of WiFi-Calling / VoLTE capability.  
  - Go to *Settings > Connections > SIM manager*, then toggle SIM 1/2 off and on again.  
  - *Thanks to Fun-Flight4427 and ActualMountain7899 for the fix.*

- ...and probably some other stuff we haven't documented yet.

**After you switch APIs with this profile enabled:**  
Go to *Settings > Connections > SIM manager*, then toggle SIM 1/2 off and back on.
<br>
</details>

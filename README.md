<p align="center">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/Repository%20Stuff/video/gama_title_video.gif" width="100%" alt="GAMA Ultra 4K Banner">
</p>

<br>


# ✅ **Overview**

**GPU API Manager for Android-based devices (GAMA) is an application that lets you switch the GPU rendering API on your Android device without requiring root access**

While optimized for the Galaxy S23 lineup, this project is compatible with any Samsung device running One UI or newer and aims to provide:

* ❄️ **Lower-Running Temps**
* 🔋 **Better Battery Life**
* 🔓 **Zero Risk** - Root is not required, 100% Knox-safe.
* 🛠️ **User Friendly** - Simple interface to toggle settings without complex terminal commands.

<br>

<p align="center">
  <img src="https://img.shields.io/badge/Android-B3E5FC?style=for-the-badge&logo=android&logoColor=333" />
  <img src="https://img.shields.io/badge/Galaxy_S23-C8E6C9?style=for-the-badge&logo=samsung&logoColor=333" />
  <img src="https://img.shields.io/badge/One_UI_7-F8BBD0?style=for-the-badge" />
  <img src="https://img.shields.io/badge/No_Root-E1BEE7?style=for-the-badge" />
</p>

<br>

# **Get started on your platform**

* 🤖 [**Android**](https://github.com/popovicialinc/gama/blob/main/README.md#gama-for-android)
* 🖥️ [**Windows**](https://github.com/popovicialinc/gama/blob/main/README.md#gama-for-windows-batch)
* 🐧 [**Linux**](https://github.com/Ameen-Sha-Cheerangan/s23-vulkan-support) (adapted by Ameen Sha Cheerangan)
* 🍎 [**MacOS Version**](#) (Coming soon by bialobrzeskid)

<br>

# **GAMA for Android**

## 🧩 **Prerequisites**

* **Your Android device**
* [**The latest .apk of GAMA**](https://github.com/popovicialinc/gama/releases/latest)
* [**Shizuku**](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)
  * Alternatively, download straight from [Shizuku's official GitHub repository](https://github.com/RikkaApps/Shizuku/releases/latest)

## 📦 **Installation & Usage**

* Install **Shizuku** on your device and [*start the service*](https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging)
  * To enable Developer Options:
    * Settings > About phone > Software information
    * Tap **Build number** 7 times, input your password (if you have one), and then you'll be asked "Allow development settings?
    * Tap OK
* Install the latest **GAMA** APK on your device.
* Open **GAMA**. The app will detect **Shizuku** and **request permission** to run commands - **grant permission**.
  * If **GAMA** displays "**Permission needed**⚠️"
    * Open **Shizuku** and select the **second option** from the top labeled *Authorized Applications*
    * **Make sure the toggle next to **GAMA** is turned on**.
    * *Clear **GAMA** from your Recents menu* so the app can refresh and check for permissions again.
    * Once completed, it should display "**Shizuku is running**✅".
* Click on either:
  * **Vulkan** - Lower temps, Better battery
  * **OpenGL** - Fallback option
 
*In the bottom-left corner of the app, there’s a button with three dots - that’s the settings menu. From there, you can choose your preferred theme (Auto, Dark or Light mode) and toggle visual effects on or off, such blur or animations, to suit your preference.*

**Note**: If prompted, allow installation from unknown sources. (Settings > Apps > (in the top-right corner) Press the 3-dot button > Special access > Install unknown apps > Allow for your browser or file manager).

As simple as that!

## 📷 **Photos**
<div>
  <table border=0>
    <tr>
      <td align="center">
        <img src="https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/Repository%20Stuff/gama_android_1.png" width="250"><br>
        <sub>Main Menu</sub>
      </td>
      <td align="center">
        <img src="https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/Repository%20Stuff/gama_android_2.png" width="250"><br>
        <sub>Settings / Dark Mode</sub>
        <td align="center">
        <img src="https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/Repository%20Stuff/gama_android_3.png" width="250"><br>
        <sub>Settings / Light Mode</sub>
      </td>
    </tr>
    <tr>
      <td align="center">
        <img src="https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/Repository%20Stuff/gama_android_4.png" width="250"><br>
        <sub>Visual Effects</sub>
      </td>
      <td align="center">
        <img src="https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/Repository%20Stuff/gama_android_5.png" width="250"><br>
        <sub>SystemUI/other services will be stopped temporarily</sub>
      </td>
    </tr>
  </table>
</div>

<br>

<br>


# **GAMA for Windows (Batch)**

*DEPRECATED IN FAVOUR OF "**Gama** for Android"*

## 🧩 **Prerequisites**
* **A Windows PC**
* **Your Android device** ([with USB Debugging enabled](https://github.com/popovicialinc/gama/blob/main/README.md#to-enable-usb-debugging))
* **The latest version of** [**GAMA**](https://github.com/popovicialinc/gama/releases/latest).

## 📦 **Installation & Usage**
* Extract the .zip archive of **GAMA** 
* Connect your device via USB to your PC
  * Ensure [**USB Debugging**](https://github.com/popovicialinc/gama/tree/main?tab=readme-ov-file#to-enable-usb-debugging) is ON and only one device is connected.
* Run "GAMA.bat"
  * A user-friendly main menu will pop up. Don't worry, everything is well-explained and designed to be simple-to-use. You can't break anything.
  * TIP: Don't run "GAMA.bat" as administrator, it breaks the UI!
* Enjoy!

**Heads up: You’ll need to run this script after every phone reboot.**

### How to enable USB Debugging

1. Settings > About phone > Software information
2. Tap **Build number** 7 times until you see "Developer mode has been turned on".
3. Go back to **Settings** > **Developer options**.
4. Scroll down, find **USB Debugging**, and toggle it **ON**.

You're ready to race!

## 📷 **Photos**
<div>
  <table border=0>
    <tr>
      <td align="center">
        <img src="https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/Repository%20Stuff/gama_main_menu.png" width="399"><br>
        <sub>Main Menu</sub>
      </td>
      <td align="center">
        <img src="https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/Repository%20Stuff/gama_settings_renderer_vulkan.png" width="350"><br>
        <sub>Choosing Aggressiveness Profile</sub>
      </td>
    </tr>
    <tr>
      <td align="center">
        <img src="https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/Repository%20Stuff/error.png" width="350"><br>
        <sub>Error State</sub>
      </td>
      <td align="center">
        <img src="https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/Repository%20Stuff/success.png" width="350"><br>
        <sub>Success Message</sub>
      </td>
    </tr>
  </table>
</div>

<br>

<br>

# ⚠️ Known Issues

<details>
<summary><b>🔸 Caused by Vulkan (App Compatibility)</b></summary>
<br>
<ul>
  <li><b>Not all apps will run under Vulkan.</b> Some will revert back to OpenGL automatically.</li>
  <li><b>Impact:</b> A great majority of apps installed on your device will run under Vulkan flawlessly. If an app reverts, it's normal behavior.</li>
</ul>
</details>

<details>
<summary><b>🔸 Caused by **GAMA** (Batch "Aggressive" Profile Side Effects - Windows only)</b></summary>
<br>
  <p><b>⚠️ "Aggressive" Profile Warning</b></p>
  <p>Using the <b>Aggressive</b> profile for stopping background apps is nuclear. While 99% of users won't need this, be aware of the side effects:</p>
  <ul>
    <li>🛑 <b>Resets Defaults:</b> Your default browser and keyboard will be reset.</li>
    <li>📵 <b>Connectivity Loss:</b> Possible loss of WiFi-Calling/VoLTE capability.</li>
    <li><b>The Fix:</b> Go to <i>Settings > Connections > SIM manager</i>, then toggle SIM 1/2 off and back on.</li>
  </ul>
  <p><i>(Thanks to Fun-Flight4427 and ActualMountain7899 for the fix)</i></p>
</details>

<p align="center">
  <img src="https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/Repository%20Stuff/video/gama_title_video.gif" width="100%" alt="GAMA Ultra 4K Banner">
</p>

# ✅ Overview

**GAMA (short for *GPU API Manager for Android-based devices*) is a Batch script that lets you switch the GPU rendering API on your Android device without requiring root access**

Whilst optimized for the Galaxy S23 lineup, this project is compatible with any Samsung device running One UI 7 or newer and aims to provide:

* ❄️ **Cooler Temp** - Better efficiency, less heat

* 🔋 **Better Battery Life** - Better efficiency, better battery life

* 🔓 **Zero Risk** - Root is not required, 100% Knox-safe.

<br>
<p align="center">
  <img src="https://img.shields.io/badge/Android-B3E5FC?style=for-the-badge&logo=android&logoColor=333" />
  <img src="https://img.shields.io/badge/Galaxy_S23-C8E6C9?style=for-the-badge&logo=samsung&logoColor=333" />
  <img src="https://img.shields.io/badge/One_UI_7-F8BBD0?style=for-the-badge" />
  <img src="https://img.shields.io/badge/No_Root-E1BEE7?style=for-the-badge" />
</p>

# 📷 Photos
<div>
  <table border=0>
    <tr>
      <td align="center">
        <img src="https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/Repository%20Stuff/gama_main_menu.png" width="350"><br>
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


# 🖥️ Other Platforms

**GAMA is now available on other platforms!** Check out these ports maintained by the community:

* 🐧 [**Linux Version**](https://github.com/Ameen-Sha-Cheerangan/s23-ultra-vulkan-linux-script) (adapted by Ameen Sha Cheerangan)

* 🍎 [**MacOS Version**]() (Coming soon by bialobrzeskid)


# 🧩 Prerequisites
* **A Windows PC**
* **Your Android device** ([with USB Debugging enabled](https://github.com/popovicialinc/gama/blob/main/README.md#to-enable-usb-debugging))
* **The latest version of** [**GAMA**](https://github.com/popovicialinc/gama/releases/latest).

# 📦 Installation & Usage
* Extract the .zip archive of GAMA 
* Connect your device via USB to your PC
  * Ensure [**USB Debugging**](https://github.com/popovicialinc/gama/tree/main?tab=readme-ov-file#to-enable-usb-debugging) is ON and only one device is connected.
* Run "GAMA.bat"
  * A user-friendly main menu will pop up. Don't worry, everything is well-explained and designed to be simple-to-use. You can't break anything.
* Enjoy!

**Heads up: You’ll need to run this script after every phone reboot.**

## How to enable USB Debugging

1. Settings > About phone > Software information

3. Tap **Build number** 7 times until you see "Developer mode has been turned on".

5. Go back to **Settings** > **Developer options**.

7. Scroll down, find **USB Debugging**, and toggle it **ON**.

You're ready to race!

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
<summary><b>🔸 Caused by GAMA ("Aggressive" Profile Side Effects)</b></summary>
<br>
<blockquote>
  <p><b>⚠️ "Aggressive" Profile Warning</b></p>
  <p>Using the <b>Aggressive</b> profile for stopping background apps is nuclear. While 99% of users won't need this, be aware of the side effects:</p>
  <ul>
    <li>🛑 <b>Resets Defaults:</b> Your default browser and keyboard will be reset.</li>
    <li>📵 <b>Connectivity Loss:</b> Possible loss of WiFi-Calling/VoLTE capability.</li>
    <li><b>The Fix:</b> Go to <i>Settings > Connections > SIM manager</i>, then toggle SIM 1/2 off and back on.</li>
  </ul>
  <p><i>(Thanks to Fun-Flight4427 and ActualMountain7899 for the fix)</i></p>
</blockquote>
</details>

# ✨ Frequently asked questions

<details>
<summary><b>Why bother with Vulkan?</b></summary>
Vulkan is a newer, low‑overhead graphics API. One UI 7 Beta 1 defaulted to Vulkan, and users loved it! Device temperatures were cool, and battery life was strong. Beta 2 reverted to OpenGL, bringing back overheating and battery drain. GAMA forces Vulkan back on.
</details>

<details>
<summary><b>Is it safe? Will it trip Knox?</b></summary>
GAMA is <b>100% Knox-safe</b> and warranty-friendly. It leverages official ADB commands - no system hacks, no root, and no bootloader unlocking.
</details>

<details>
<summary><b>Do I have to run GAMA after every reboot?</b></summary>
Yes, unfortunately. Android resets the global graphics driver preference on restart.
</details>

<details>
<summary><b>What if I don’t notice a difference?</b></summary>
Some devices and apps won’t show a huge change immediately, but Vulkan generally offers better long-term thermal management. If you’re not seeing improvement, your phone might already be well-optimized.
</details>

<details>
<summary><b>Can I undo this?</b></summary>
Yup. GAMA natively supports switching back: <b>Vulkan-to-OpenGL</b>. Alternatively, just restart your phone to reset everything to stock.
</details>

<details>
<summary><b>Why isn’t this a built-in setting?</b></summary>
Great question — ask Samsung. If you ask me, this smells a whole lot like planned obsolescence... but anyway.
</details>

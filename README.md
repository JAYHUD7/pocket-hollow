# Pocket-Hollow — Star Fox Adventures Android Port (ARM64)

An unofficial **Star Fox Adventures (Starfox Adventures) Android ARM64 port** and controller-first mobile launcher for Foxhollow, built for Android gaming handhelds and modern Snapdragon, MediaTek Dimensity, Google Tensor, and Samsung Exynos devices.

> **Disclaimer:** Pocket-Hollow is an independent, fan-made port and is **not** affiliated with, endorsed by, or connected to Nintendo, Rare, or the upstream Foxhollow project. You must supply your own legally obtained GameCube disc backup (`game.iso`). No copyrighted game files or BIOS assets are included.

---

## Quick Setup
1. Download the latest **`pocket-hollow.apk`** from the [Releases](https://github.com) page and install it on your Android device.
2. Open **Pocket-Hollow** and allow **All Files Access** so the launcher can read your game folder (`/storage/emulated/0/pocket-hollow/`).
3. Tap **Select ISO** and choose your own legal **`game.iso`** backup (`USA v1.0` or `v1.1`).
4. *(Optional)* Customize resolution scale, aspect ratio, mods (**HD Texture Pack (4x)**, **Water Reflections**, **Hold-A Auto-Mash**), or telemetry overlays, then press **LAUNCH GAME**.

---

## Hardware & Driver Support
* **Auto (Recommended) Driver Mode:** Automatically detects your hardware GPU on startup and selects the safest Vulkan driver and starting resolution scale:
  * **Snapdragon Adreno 740 / 750 (8 Gen 2 / 8 Gen 3 / G3x Gen 2):** Bundled **Turnip CI** driver (`2.0x` default scale)
  * **Snapdragon Adreno 618 – 660 (855 / 865 / 870 / 778G):** Bundled **Turnip CI** driver (`1.0x–1.5x` default scale)
  * **Snapdragon Adreno 710 – 735 (8 Gen 1 / 8s Gen 3) & Adreno 8xx (8 Elite / G3x Gen 3):** Native **System Vulkan** (`1.5x–2.0x` default scale)
  * **MediaTek Dimensity / Google Tensor (Mali / Immortalis) & Samsung Exynos (Xclipse):** Native **System Vulkan** (`1.0x–2.0x` default scale)
* **Manual Overrides:** Switch between **Auto**, **Bundled Turnip CI**, **Bundled Turnip (A8xx)**, **Bundled Qualcomm (v840)**, **System Default**, or **Custom Driver** anytime under **GRAPHICS**.

---

## Key Features
* **All-in-One Offline APK:** Includes bundled Vulkan drivers, pre-warmed shader caches, and optional 4x HD textures—no external downloads required.
* **Clean First-Boot Defaults:** All mods and telemetry overlays start **OFF** so players choose their own setup; enabling **HD Texture Pack (4x)** unpacks cleanly with a live progress bar upon pressing **LAUNCH GAME**.
* **1.5 GB HD Texture Cache:** Keeps multi-zone 4x textures decoded in RAM simultaneously for stutter-free area transitions.
* **Handheld & Controller First:** Full physical gamepad navigation in the launcher, unbuffered low-latency Bluetooth controller polling, and optional **Hold-A Auto-Mash** for strength challenges.
* **Android Camera & Gameplay Fixes:** Includes a 3-ray third-person camera wall-standoff collision fix, Tricky command target focus filtering, scoped Krazoa Palace final ledge leap threshold, and restored in-game **Normal / Wide** screen toggle.
* **Live Telemetry Pill:** Optional MangoHud-style overlay showing whole-number **FPS**, frame time (`ms`), SoC temperature, battery wattage, per-process **RAM**, and peak CPU clock.
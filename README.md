# Lemuroid PS2

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.swordfish.lemuroid/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.swordfish.lemuroid)

## Description

Lemuroid is an open-source emulation project for Android based on Libretro. Its main goal is ease of use, good Android integration and a great user experience.

It originated from a rib of [Retrograde](https://github.com/retrograde/retrograde-android), but graduated to a standalone project integrating [LibretroDroid](https://github.com/Swordfish90/LibretroDroid).

|Screen 1|Screen 2|Screen 3|
|---|---|---|
|![Screen1](https://github.com/Swordfish90/Lemuroid/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg)|![Screen2](https://github.com/Swordfish90/Lemuroid/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg)|![Screen3](https://github.com/Swordfish90/Lemuroid/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg)|

This fork adds PlayStation 2 support to Lemuroid through [PCEE2](https://github.com/WizzardSK/pcee2-libretro), the GPL-3.0+ libretro port of current PCSX2. It is not affiliated with Lemuroid, PCEE2, or PCSX2.

### Supported Systems:
- Atari 2600 (A26) ([stella](https://docs.libretro.com/library/stella/))
- Atari 7800 (A78) ([prosystem](https://docs.libretro.com/library/prosystem/))
- Atari Lynx (Lynx) ([handy](https://docs.libretro.com/library/handy/))
- Nintendo (NES) ([fceumm](https://docs.libretro.com/library/fceumm/))
- Super Nintendo (SNES) ([snes9x](https://docs.libretro.com/library/snes9x/))
- Game Boy (GB) ([gambatte](https://docs.libretro.com/library/gambatte/))
- Game Boy Color (GBC) ([gambatte](https://docs.libretro.com/library/gambatte/))
- Game Boy Advance (GBA) ([mgba](https://docs.libretro.com/library/mgba/))
- Sega Genesis (aka Megadrive) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega CD (aka Mega CD) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega Master System (SMS) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega Game Gear (GG) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Nintendo 64 (N64) ([mupen64plus](https://docs.libretro.com/library/mupen64plus/))
- PlayStation (PSX) ([PCSX-ReARMed](https://docs.libretro.com/library/pcsx_rearmed/))
- PlayStation Portable (PSP) ([ppsspp](https://docs.libretro.com/library/ppsspp/))
- PlayStation 2 (PS2) (PCEE2 / PCSX2-based Libretro core, ARM64 only)
- FinalBurn Neo (Arcade) ([fbneo](https://github.com/libretro/FBNeo/))
- Nintendo DS (NDS) ([desmume](https://docs.libretro.com/library/desmume/)/[MelonDS](https://docs.libretro.com/library/melonds/))
- NEC PC Engine (PCE) ([beetle_pce_fast](https://docs.libretro.com/library/beetle_pce_fast/))
- Neo Geo Pocket (NGP) ([mednafen_ngp](https://docs.libretro.com/library/beetle_neopop/))
- Neo Geo Pocket Color (NGC) ([mednafen_ngp](https://docs.libretro.com/library/beetle_neopop/))
- WonderSwan (WS) ([beetle_cygne](https://docs.libretro.com/library/beetle_cygne/))
- WonderSwan Color (WSC) ([beetle_cygne](https://docs.libretro.com/library/beetle_cygne/))
- Nintendo 3DS (3DS) ([citra](https://docs.libretro.com/library/citra/))

### Features:
- Android TV support
- Automatically save and restore game states.
- ROMs scanning and indexing
- Optimized touch controls
- Quick save/load
- Support for Zipped ROMs
- Display simulation (LCD/CRT)
- Gamepad support
- Local multiplayer
- Tilt input
- Customizable touch controls (size and position)
- Cloud save sync
- HD mode

### Languages:
You can help translate Lemuroid in your native language by going here: https://crowdin.com/project/lemuroid

## PlayStation 2

PS2 support is available on Android ARM64 (`arm64-v8a`) devices running Android 7.0/API 24 or newer. PCEE2 uses Vulkan by default, with surfaceless OpenGL and software-renderer fallback paths implemented by the core. Performance and compatibility depend on the device and game.

Place PS2 games in a directory whose name includes `ps2` (for example, `Games/ps2`). This lets Lemuroid distinguish PS2 images from the PSX/PSP formats that share file extensions. PCEE2-supported formats registered by this fork are: `iso`, `chd`, `cue`, `m3u`, `cso`, `zso`, `gz`, `bin`, `mdf`, `nrg`, `elf`, and `irx`. `.isz` is not registered because PCEE2 does not advertise it as supported.

PCEE2 requires a PS2 BIOS that you legally dump from hardware you own. No BIOS is included. Import a commonly named BIOS such as `scph39001.bin` through Lemuroid's normal library scan, or place it at:

`<Lemuroid app files>/system/pcsx2/bios/<your BIOS filename>`

PCEE2 accepts valid PS2 BIOS dumps rather than requiring one fixed filename. Its memory cards, cache, and optional configuration are stored under `<Lemuroid app files>/system/pcsx2/`; memory cards are in `pcsx2/memcards`. The core embeds its version-matched mandatory resources. Optional `patches.zip` from the PCSX2 patches project can be placed in `system/pcsx2/resources/patches.zip`.

Lemuroid streams/caches full-path content through its existing storage provider path. PS2 images are never loaded wholly into RAM by this integration, but content providers and compressed formats can require substantial temporary disk space.

PS2 gets its own DualShock 2 touch layout (`PS2Left`/`PS2Right`): D-pad, both analog sticks with L3/R3 press buttons, Cross, Circle, Square, Triangle, L1/L2/R1/R2, Start, and Select. PCEE2 maps these as DualShock 2 inputs and supports controller rumble. Its libretro savestate implementation is enabled, although states remain core-version-specific.

## Building

Initialize all source dependencies, including the pinned PCEE2 and Lemuroid core submodules:

```bash
git submodule update --init --recursive
```

Install Android SDK platform 35, Build Tools 34.0.0, CMake, Ninja, JDK 17, and an Android NDK. The CI workflow builds against the runner's preinstalled latest NDK (`$ANDROID_NDK_LATEST_HOME`); a pinned older NDK caused a deterministic clang toolchain failure, so use a recent NDK. Then build and stage PCEE2 before the app:

```bash
export ANDROID_NDK_ROOT=/path/to/android-ndk
JOBS=2 bash scripts/build-pcee2-android.sh
./gradlew :lemuroid-app:assembleFreeBundleRelease
```

`JOBS` caps the native build parallelism (the PCEE2 dependency recipe uses every vCPU by default, which OOMs CI runners); 2 is a safe default for a 4+ core runner. The script builds shaderc and friends into `build/pcee2/deps` (cached across CI runs), compiles the core with the system CMake/Ninja, and stages it so Gradle picks it up.

The staged core is `build/pcee2-jni/arm64-v8a/libpcee2_libretro_android.so`. The APK is `lemuroid-app/build/outputs/apk/freeBundle/release/lemuroid-app-free-bundle-release.apk`.

## Releases

`.github/workflows/build.yml` builds PCEE2, builds a bundled ARM64-capable release APK, verifies the native library is packaged, uploads it as an artifact, and creates a GitHub Release. It can be triggered manually from the Actions tab (`workflow_dispatch`) or by pushing a tag such as `v1.0.0`, which creates `Lemuroid-PS2-1.0.0-arm64-v8a.apk` and names the APK from the version tag; untagged runs name the APK after the branch. The workflow derives the application version name from the tag.

The release workflow generates an ephemeral CI signing key so a clean runner can produce an installable APK. Configure a maintained signing key before distributing production builds outside this repository.

## Licensing and Attribution

Lemuroid is GPL-3.0-or-later. PCEE2 and its PCSX2-derived emulation code are GPL-3.0-or-later; see the pinned `pcee2-libretro` submodule and its `COPYING.GPLv3` for source and attribution. PCEE2 is maintained by WizzardSK and tracks upstream PCSX2; it is not affiliated with the PCSX2 team.

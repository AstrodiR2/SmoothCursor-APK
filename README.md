<div align="center">

<h1>SmoothCursor</h1>

<p><b>Neovide-style smooth cursor, neon glow, fade-in text and custom wallpaper for Termux</b></p>

<p>
  <a href="README.md">🇬🇧 English</a> · <a href="README.ru.md">🇷🇺 Русский</a>
</p>

<p>
  <a href="https://github.com/AstrodiR2/SmoothCursor-APK/releases/latest"><img alt="Last release" src="https://img.shields.io/github/v/release/AstrodiR2/SmoothCursor-APK?style=for-the-badge&label=Last%20release&color=2ea043"></a>
  <a href="https://github.com/AstrodiR2/SmoothCursor-APK/releases/latest"><img alt="Download Termux" src="https://img.shields.io/badge/Download%20Termux-APK-3ddc84?style=for-the-badge&logo=android&logoColor=white"></a>
</p>

</div>

> ## ✅ No root. No LSPosed. No Xposed.
> 👉 My previous project: **[github.com/AstrodiR2/SmoothCursor](https://github.com/AstrodiR2/SmoothCursor)** worked **only with root** (Xposed/LSPosed module).
> **Root is no longer needed.** SmoothCursor is now built straight into the Termux APK: install the APK and it just works. That old repo is outdated, use this one.

<sub>Unofficial fork of [Termux](https://github.com/termux/termux-app).</sub>

Made by **[AstrodiR2](https://github.com/AstrodiR2)**.

---

## Features

- Smooth animated cursor (Neovide-like), with configurable color, outline and trail
- Smooth scrolling with adjustable speed
- Padding, glow (shared or separate for text and cursor) and smooth text fade-in
- All settings in the app, nothing to configure externally
- Monet / Material You theming (`Termux:Monet`)

**Not ported on purpose:** image/background/splash settings and the iTerm2/kitty graphics protocols.

## Installation

1. Open the **[Actions](../../actions)** tab of this repository, pick the latest successful **Build APK** run and download the artifact `termux-monet-universal` (or [Releases](../../releases) if available).
2. Unzip the artifact and install the `.apk` (allow "Install unknown apps" for your browser/file manager).
3. Open the app. No extra modules, no reboot.

> ⚠️ **Read before installing over an existing Termux**
> This build is signed with a different key than Termux from F-Droid / GitHub / Play Store, and uses the same package name `com.termux`. Android will **refuse to update** over it, so you must uninstall the old Termux first, which **deletes all its data**.
> Back up first, inside the old Termux:
> ```bash
> termux-setup-storage
> tar -czf /sdcard/termux-backup.tar.gz -C /data/data/com.termux/files ./home ./usr
> ```
> After installing this build, restore:
> ```bash
> tar -xzf /sdcard/termux-backup.tar.gz -C /data/data/com.termux/files --recursive-unlink --preserve-permissions
> ```
> Termux plugins (Termux:API, Boot, Widget, …) must be signed with the same key as the main app, so use matching builds.

## Usage / Tutorial

1. **Long-press** the terminal to open the context menu.
2. Tap **SmoothCursor settings**.
3. Tweak the cursor (color, outline, trail), scrolling speed, padding, glow and text fade-in.
4. Changes apply right away.

## Building it yourself

Requires JDK 17 and the Android SDK:

```bash
./gradlew :app:assembleDebug
```

APKs (universal + per-ABI) end up in `app/build/outputs/apk/debug/`.

Or let GitHub do it: push to your repo and run the **Build APK** workflow (`.github/workflows/build.yml`), then download the APKs from the run's *Artifacts*.

## Forking & credits

Forks and modifications are welcome, but **please credit the author**: keep the name **AstrodiR2** and/or a link to the original repository
(<https://github.com/AstrodiR2/SmoothCursor>) in your README. Thank you!

## Based on

- [Termux](https://github.com/termux/termux-app), the terminal app this build is based on (upstream README kept in [`docs/upstream/`](docs/upstream/TERMUX_UPSTREAM_README.md))
- The original [SmoothCursor Xposed module](https://github.com/AstrodiR2/SmoothCursor)

## License

Termux and its components are licensed under **GPLv3** (and other licenses listed in the `LICENSE*` files). Derivative works must stay under the same terms.

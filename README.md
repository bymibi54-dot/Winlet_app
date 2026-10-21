# Winlet

A lightweight Android launcher that gives any phone a desktop-style experience: taskbar, Start menu, floating resizable windows, quick panel. No external display needed.

لانشر أندرويد خفيف يعطي أي هاتف تجربة سطح المكتب: شريط مهام، قائمة ابدأ، نوافذ عائمة، ولوحة سريعة، بدون شاشة خارجية.

## Features
- Windows 11-style taskbar: centered Start logo, search pill, pinned and running apps, system tray (Wi-Fi, volume, battery), clock, notifications
- Start menu with search
- Long-press menu: floating / snap left / snap right / maximize / pin / app info / uninstall / close
- Quick panel: battery, volume, Wi-Fi, Bluetooth, torch, close all
- Uses your system wallpaper, no permissions prompts

## Setup
1. Build with Android Studio and install.
2. Developer options: enable **Enable freeform windows** and **Force activities to be resizable**, then reboot.
3. Settings > Default apps > Home app > **Winlet**.

## Limitations
- Freeform launching relies on the hidden `ActivityOptions.setLaunchWindowingMode` via reflection. Behavior varies by manufacturer and Android version.
- Android does not let a launcher close another app's visible window; "Close" removes it from the taskbar and stops background processes only.
- Snap left/right applies when an app is opened from Winlet; close an already-open window first.

## License
MIT

## First-run setup
On first launch Winlet shows a checklist: Developer options, Freeform windows, Resizable activities, default Home app, and (optional) Usage access. It re-checks every time you come back from Settings. You can reopen it anytime from the quick panel (tap the clock) > **Setup & requirements**.

Optional (advanced): run once from a computer
`adb shell pm grant app.winlet.launcher android.permission.WRITE_SECURE_SETTINGS`
and Winlet can switch the freeform options on for you (reboot still required).

## Open-app tracking
With **Usage access** granted (Android 10+), the taskbar lists every app that has a live window, including apps not started from Winlet. Apps that go fully to the background disappear from the bar. Without it, only apps opened from Winlet are shown.

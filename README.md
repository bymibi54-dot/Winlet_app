# Winlet

A lightweight Android launcher that gives any phone a desktop-style experience: taskbar, Start menu, floating resizable windows, quick panel. No external display needed.

لانشر أندرويد خفيف يعطي أي هاتف تجربة سطح المكتب: شريط مهام، قائمة ابدأ، نوافذ عائمة، ولوحة سريعة، بدون شاشة خارجية.

## Features
- Taskbar with pinned apps and running-app indicators
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

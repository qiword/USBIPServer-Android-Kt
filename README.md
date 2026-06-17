# USB/IP Server for Android (Kotlin)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[中文版](README.zh.md) | English

Forked from [cgutman/USBIPServerForAndroid](https://github.com/cgutman/USBIPServerForAndroid) and fully rewritten.

Share USB devices connected to your Android device over a TCP/IP network using the USB/IP protocol.

## What's Changed from the Original

| Aspect | Original | This Fork |
|--------|----------|-----------|
| **Language** | Java | Kotlin |
| **USB I/O** | C JNI (ndk-build) | JNA (pure Kotlin, no native compile) |
| **TCP Server** | Java ServerSocket | Java ServerSocket (stable) |
| **UI** | Basic Activity | Material 3 + RecyclerView + DrawerLayout |
| **Theming** | Legacy android:Theme | Material 3 DayNight (dark mode) |
| **Battery** | None | Battery optimization exemption settings |
| **Device filtering** | All devices exposed | Per-device Share/Unshare control |

## Features

- Export USB devices over USB/IP protocol (port 3240)
- Support for Bulk, Interrupt, and Control transfers
- Per-device Share/Unshare via Binder
- Material 3 UI with dark mode support
- Battery optimization exemption guide
- No root required
- No native compilation needed (JNA calls libc ioctl directly)

## Requirements

- Android 5.0+ (API 21)
- USB Host support

## Building

```bash
./gradlew assembleDebug
```

Or use `build.bat` for one-click build + install:

```batch
build.bat
```

## Usage

1. Open the app, tap **Start Service**
2. Plug in a USB device
3. Tap **Share** on the device (grant permission if prompted)
4. On your USB/IP client machine:

```bash
# List devices
sudo usbip list -r <android-ip>

# Attach device
sudo usbip attach -r <android-ip> -b <busid>
```

## License

GPL-3.0 — same as the original project.

```
Copyright (C) 2024 cgutman
Copyright (C) 2026 USB/IP Server for Android (Kotlin) contributors
```

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

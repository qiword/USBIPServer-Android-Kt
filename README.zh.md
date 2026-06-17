# USB/IP Server for Android (Kotlin)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[English](README.md) | 中文版

从 [cgutman/USBIPServerForAndroid](https://github.com/cgutman/USBIPServerForAndroid) fork 并完全重写。

将 Android 设备上连接的 USB 设备通过 USB/IP 协议以 TCP/IP 网络共享给远程客户端。

## 与原项目的差异

| 方面 | 原始项目 | 本 Fork |
|------|---------|--------|
| **语言** | Java | Kotlin |
| **USB I/O** | C JNI (ndk-build) | JNA（纯 Kotlin，无需 native 编译） |
| **TCP 服务器** | Java ServerSocket | Java ServerSocket（稳定可靠） |
| **UI** | 基础 Activity | Material 3 + RecyclerView + DrawerLayout |
| **主题** | 旧版 android:Theme | Material 3 DayNight（暗色模式） |
| **电池优化** | 无 | 电池优化豁免设置引导 |
| **设备过滤** | 暴露所有设备 | 每个设备独立 Share/Unshare 控制 |
| **构建** | 需要 C NDK | 纯 Gradle，一步构建 |

## 功能

- 通过 USB/IP 协议导出 USB 设备（端口 3240）
- 支持 Bulk、Interrupt、Control 三种传输类型
- 每个设备独立 Share/Unshare 控制（Binder 通信）
- Material 3 UI，支持暗色模式
- 息屏保活引导
- 无需 Root 权限
- 无需 native 编译（JNA 直接调用 libc ioctl）

## 系统要求

- Android 5.0+ (API 21)
- 支持 USB Host

## 构建

```bash
./gradlew assembleDebug
```

或使用 `build.bat` 一键构建+安装：

```batch
build.bat
```

## 使用方法

1. 打开 App，点击 **启动服务**
2. 插入 USB 设备
3. 点击设备的 **Share** 按钮（首次需授权）
4. 在 USB/IP 客户端机器上：

```bash
# 查看可用设备
sudo usbip list -r <安卓设备IP>

# 连接设备
sudo usbip attach -r <安卓设备IP> -b <busid>
```

## 技术架构

```
┌──────────────────────────────────────┐
│  UsbIpConfig (Activity)              │
│  ├── Material 3 UI                   │
│  ├── RecyclerView 设备列表             │
│  └── DrawerLayout 设置面板             │
│         │ Binder                     │
│  UsbIpService                        │
│  ├── sharedDevices (Share 控制)       │
│  ├── UsbIpServer (TCP :3240)         │
│  └── USB 传输                        │
│         │ JNA                        │
│  UsbLib (libc ioctl)                 │
│         │                            │
│  Linux Kernel usbdevice_fs           │
└──────────────────────────────────────┘
```

## 许可证

GPL-3.0 — 与原项目一致。

```
Copyright (C) 2024 cgutman
Copyright (C) 2026 USB/IP Server for Android (Kotlin) 贡献者
```

本程序为自由软件，你可以依据自由软件基金会发布的 GNU 通用公共许可证
第三版或（按你意愿）任何后续版本的条款，重新分发和/或修改它。

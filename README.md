# BoxRemoteKey

电视盒子的远程输入法与跨屏控制工具，可跨屏远程输入、远程控制盒子、远程文件管理、HTTP/RTMP/MMS 网络视频直播、ED2K/种子文件的视频边下边播、视频投屏（DLNA）。

> 本项目基于 小盒精灵 [cacaview/TVRemoteIME](https://github.com/cacaview/TVRemoteIME) 修改而来。

---

## 主要改动

本项目在上游基础上进行了功能增强与问题修复：

- **新增中文输入法功能**：在原有英文软键盘基础上，完善了中文（拼音）输入引擎与候选词面板，支持在电视盒子端通过物理键盘或远程键盘进行中文输入，候选词可上屏、翻页、数字选词。
- **重新布置键盘布局**：重新设计了 TV 端虚拟键盘的按键排列、修饰键（退格/符号/中英切换/大小写）与样式，优化了焦点导航逻辑，更适合遥控器方向键操作。
- **修复上传文件和 APK 的功能**：修复了控制端远程上传文件、远程安装 APK 时出现的接口异常与兼容性问题，使跨屏传送与远程安装恢复正常可用。

此外，构建环境也进行了现代化升级：Gradle 8.13、Android Gradle Plugin 8.13.2、compileSdk 36、JDK 17，并修复了 Android 5.0+ 显式 Intent 兼容性与 ProGuard 配置问题。

---

## 它能做什么

- **跨屏输入**：用手机/电脑/IPAD 在盒子里输入文字，要多快有多快
- **远程控制**：代替盒子遥控器，支持触控板/鼠标控制（基于辅助功能服务）
- **应用管理**：远程启动盒子里的应用
- **文件管理**：跨屏安装应用、传送文件到盒子，远程浏览/复制/剪切/删除文件
- **视频播放**：HTTP/RTMP/MMS 网络视频直播、ED2K/种子文件边下边播
- **视频投屏**：支持 DLNA 协议，手机/IPAD/电脑的视频可投屏到电视上播放

---

## 安装方法

### 一、通过 adb 命令安装

1、电视盒子开启 adb 调试

2、电脑通过 adb 命令连接电视盒子（假如电视盒子的内网 ip 为：192.168.1.100）
```
adb connect 192.168.1.100:5555
```
注意，手机要与盒子在同一个 WIFI 网络（内网网络）。执行 `adb devices` 命令显示有 device 列表，则表示已连接上盒子，可继续下一步。

3、通过以下命令安装输入法 apk 包
```
adb install IMEService-debug.apk
```

4、设置为系统默认输入法
```
adb shell ime set com.android.boxremotekey/.IMEService
```

> 注：如果无法设置为系统的默认输入法，则先启动 BoxRemoteKey 应用后再手动启动服务。如需使用远程输入及远程遥控功能还需要开启盒子的 ADB 模式。

5、电脑或者手机浏览器访问远程输入法的控制页面
```
http://192.168.1.100:9978/
```

### 二、通过 U 盘或者其它方式安装

1、安装后在盒子应用列表里找到 **BoxRemoteKey** 的图标点击运行

2、根据应用的提示进行设置即可。

---

## 触控板/鼠标控制（基于辅助功能服务）

使用 Android AccessibilityService 实现远程触控板功能。

**API 端点：**

| 端点            | 方法 | 说明                                 |
| --------------- | ---- | ------------------------------------ |
| `/mouse/status` | GET  | 获取服务状态和鼠标位置               |
| `/mouse/move`   | POST | 移动鼠标 (参数: dx, dy)              |
| `/mouse/click`  | POST | 点击 (参数: button - 0=左键, 1=右键) |
| `/mouse/scroll` | POST | 滚动 (参数: dy)                      |
| `/mouse/show`   | POST | 显示光标                             |
| `/mouse/hide`   | POST | 隐藏光标                             |

**使用方法：**

1. 安装应用后，进入系统设置 → 辅助功能
2. 找到 **BoxRemoteKey** 并启用
3. 通过网页控制端即可使用触控板功能，屏幕上会显示鼠标光标

---

## 视频播放功能说明

1、**本地视频文件**：通过传送功能将手机、电脑等控制端的视频文件传送到盒子后会自动播放

2、**种子文件**：通过传送功能将种子文件传送到盒子后会自动播放种子里的第一个视频文件

3、**网络视频**（http/rtmp/mms 协议的直播或 thunder/ed2k 协议的视频）：直接在网络视频地址框输入视频 URL，点击“远程播放”按钮盒子会自动开始播放

> 注：对于种子文件及非直播的网络视频，本应用采用边下边播方式，会占用盒子的大量空间（根据视频大小而定）。如果盒子的可用空间不够，视频会播放失败。正常播放结束时应用会自动删除边下边播的缓存文件，也可点击控制界面里的“清除缓存”删除。

## 视频播放控制说明

在视频播放时，点击控制器进行控制：左右键用于快进或快退（非直播情况下可用）、上下键用于选择需要播放的视频文件（播放种子文件且包含多个视频时可用）；播放单视频（非种子视频）时长按下键 1 秒左右会重头开始播放；确定键用于暂停或恢复播放。

---

## 电视直播源列表

需要更改直播源列表有两种方式：

1、直接在控制端界面修改

2、通过文件管理界面进入 `/Android/data/com.android.boxremotekey/files/` 目录，对其中的 `tv.txt` 文件进行修改（通过下载与上传）即可。如 `tv.txt` 文件不存在，新建即可覆盖默认的直播源。此文件格式为 ini 文件格式，格式如下：

```
[电视台名称]
源名称1 = 源地址
源名称2 = 源地址

[电视台名称]
源名称 = 源地址
```

## 视频投屏说明

只要支持 DLNA 协议的播放器（如手机迅雷、爱奇艺等）都可投屏到 BoxRemoteKey 在电视上播放。

---

## 构建

```bash
# 构建 Debug 版本（可直接安装）
./gradlew assembleDebug

# 构建 Release 版本（未签名，需要签名后才能安装）
./gradlew assembleRelease
```

**构建环境要求：** JDK 17、Android SDK 36、Gradle 8.13（已通过 gradle-wrapper 内置，无需单独安装）。

> 产物路径：`IMEService/build/outputs/apk/debug/IMEService-debug.apk`

---

## 引用第三方包/资源说明

1、[NanoHttpd](https://github.com/NanoHttpd/nanohttpd) —— 用于实现 HTTP WEB 服务

2、[ZXing](https://github.com/zxing/zxing) —— 用于实现二维码的输出

3、[AFAP Player](https://github.com/AFAP/Player) —— 用于实现视频播放，采用 [ijkplayer](https://github.com/Bilibili/ijkplayer) 播放器核心

4、[MiniThunder](https://github.com/oceanzhang01/MiniThunder) —— 用于实现视频文件下载功能

5、[AdbLib](https://github.com/cgutman/AdbLib) —— 用于连接 adb 服务，在非输入法状态下实现遥控功能

6、[DroidDLNA](https://github.com/offbye/DroidDLNA) —— 用于实现 DLNA 视频投屏服务，内部核心采用 [Cling](https://github.com/4thline/cling) DLNA 库

---

## 致谢

本项目 Fork 自 [cacaview/TVRemoteIME](https://github.com/cacaview/TVRemoteIME)，其上游追溯至 [newPersonKing/TVRemoteIME](https://github.com/newPersonKing/TVRemoteIME) 及原作者 kingthy 的 [TVRemoteIME](https://github.com/kingthy/TVRemoteIME)。感谢所有为这个项目付出过努力的开发者。

## License

本项目遵循上游的 Apache License 2.0 开源协议，详见 [LICENSE](LICENSE)。

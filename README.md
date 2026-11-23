<div align="center">

<img src="docs/banner.svg" width="760" alt="广职大课表">

**广州职业技术大学（广职大，原广州番禺职业技术学院）教务系统课表客户端**

[![License](https://img.shields.io/badge/License-GPLv3-2C6A4E.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg)](#下载安装)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)

</div>

---

## 这是什么

一个用 **Kotlin + Jetpack Compose** 写的 Android 课表 App，直接对接学校教务系统（`jiaowu.gzpyp.edu.cn/jsxsd`），登录、抓课表、识别验证码全部在手机上完成。

原本只是给自己用的小工具：教务系统每次查课表都要重新登录、点一堆菜单，手机上排版还挤成一团。所以做了这个应用，把课表变成一个打开就能看、桌面上随时可见、上课前会主动提醒你的东西。

> **非官方客户端。** 本项目与学校及教务系统提供方没有任何关联，仅供个人学习使用，因使用本应用造成的任何后果由使用者自行承担。

## 功能

**课表**
- 按周查看整学期课表，左右切换周次，一键回到本周
- 课程按名称自动配色，同一门课颜色固定
- 点课程看详情：时间、地点、教师、上课周次（含单双周）

**教务同步**
- 自动登录教务系统抓取最新课表，图形验证码**在本机识别**（详见[实现](#验证码识别)）
- 三种同步方式：启动时静默更新 / 每天定时后台更新 / 手动点刷新
- 登录态可维持数小时，平时同步不用反复输验证码

**上课提醒**
- 课前弹系统通知，可调0–60分钟
- 用精确闹钟（`USE_EXACT_ALARM`）定时，避免省电策略把它攒着一起发
- 内置「发送测试通知」和三项权限自检，方便排查“为什么没提醒”

**桌面小组件**
- 两种部件：**今日课程**（今天剩下的课）和**近期课程**（按天分组往后铺）
- 高度不够时可以上下滚动，不会再把课截掉
- 可调背景不透明度、字号、圆角、深浅配色
- 显示地点 / 教师可单独开关
- 今天课上完了会自动切到明天，并把标题换成强调色

**界面**
- 完整 Material 3：色调分层、动态取色、段式按钮、胶囊状态标签
- 浅色 / 深色 / 跟随系统三种模式，另有 Android 12+ 壁纸取色
- 今日页用不同明度区分「进行中 / 待上课 / 已结束」

## 下载安装

到 [Releases](https://github.com/Campsisian/GPU-Timetable/releases) 下载最新的 `.apk`，传到手机上安装即可。

- 系统要求：**Android 8.0（API 26）及以上**
- 支持架构：`arm64-v8a`、`armeabi-v7a`
- 安装时若提示「未知来源」，允许一次即可

> 本应用没有上架任何应用商店。请只从本仓库的 Releases 下载，不要用来路不明的安装包。

## 首次使用

1. 打开 App → **设置** → 填入学号和密码 → 点「保存并更新」
2. 等 3–5 秒（登录 + 识别验证码 + 解析课表），成功后课表页就有内容了
3. 打开「上课提醒」，允许通知权限；如果自检里「后台运行」显示受限制，点进去把电池优化设为不限制
4. 桌面长按空白处 → 小部件 → 找到「广职大课表」

## 构建

### 环境

| 项目 | 版本 |
|---|---|
| JDK | 17 |
| Android SDK | compileSdk 35、build-tools 35.0.0 |
| Gradle | 8.7（仓库已带 wrapper，不用单独装） |

### 步骤

```bash
git clone https://github.com/Campsisian/GPU-Timetable.git
cd GPU-Timetable

# 指向本机 SDK
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

跑单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

### 正式签名（可选）

仓库里不含任何证书。想自己打正式包，把 `keystore.properties.example` 复制成 `keystore.properties` 并填好：

```properties
storeFile=keystore/release.jks
storePassword=你的密码
keyAlias=你的别名
keyPassword=你的密码
```

没有这个文件时 `assembleRelease` 仍会产出 APK，只是未签名。

## 技术实现

### 验证码识别

教务系统的登录验证码是 4 位字符，带干扰线。方案是 **ddddocr 的通用模型 + 本机 ONNX 推理**，不联网、不走第三方打码平台。

- 模型：ddddocr 的 `common_old.onnx`（13 MB），放在 `assets/`，用 `onnxruntime-android` 推理
- 预处理（与 ddddocr 的 Python 实现逐项对齐）：RGB → 灰度 → 等比缩放到高 64 → LANCZOS 重采样 → 归一化 `/255` → `NCHW` → `(1, 1, H, W)`
- 解码：CTC 贪心解码，字符集来自 `assets/ocr_charset.txt`
- 实测在 460 张真实验证码上**整词准确率 89.3%**，识别失败会自动重试并提示

> 踩过的坑：一开始打包的是 `common.onnx`（54 MB 的 beta 模型），识别率直接是 0——因为 ddddocr 默认加载的是 `common_old.onnx`。换回正确的模型后准确率正常，安装包也小了 40 MB。

### 登录

- `POST /jsxsd/xk/LoginToXk`
- 字段：`userAccount`、`userPassword`、`RANDOMCODE`、`encoded`（`base64(用户名)%%%base64(密码)`）
- 失败时服务端返回登录页但 HTTP 仍是 200，所以判定要看页面内容而不是状态码

### 课表解析

以 `class="kbcontent"` 的单元格为锚点，配合方括号里的真实节次（如 `[01-02节]`）还原跨大节的课程；周次支持 `2-4,6,9-12` 和 `1-6(单)` 这类写法。

### 上课提醒

`AlarmManager` + `BroadcastReceiver`，7 天滚动窗口：每次响铃后顺手把窗口往后推一天，即使一个月不开 App 也不会断。

`targetSdk 35` 下 `SCHEDULE_EXACT_ALARM` 在 Android 14+ 默认是拒绝的，所以改用 `USE_EXACT_ALARM`（安装即授予），否则会退化成不精确闹钟，省电模式下晚十几分钟才弹。

### 桌面小组件

用 `RemoteViews` 的**集合部件**方案：部件里放 `ListView`，由 `RemoteViewsService` 提供数据行，所以课再多也能滚。

两个容易踩的点：
- RemoteViews 走 `LayoutInflater` 白名单，`<View>` 不在白名单里，用了会抛 `Class not allowed to be inflated`，背景层必须换成 `FrameLayout`
- 标题行由 provider 在构建时写入，而列表由工厂单独取数、可能用「现在」重新计算，两边时刻不一致就会出现「标题写今天、列表是明天」。解法是标题与内容共用同一个时间戳，并把标题存一份供工厂比对，不一致就整体重建

## 目录结构

```
app/src/main/java/com/gzpyp/timetable/
├── core/     JSON 解析、数据模型、教务解析、HTTP 客户端、时间与图像工具
├── ocr/      ONNX 验证码识别 + CTC 解码
├── data/     设置项与本地存储（SharedPreferences）
├── sync/     同步服务、WorkManager 定时任务、开机重启
├── notify/   上课提醒（闹钟、通知渠道、权限探测）
├── widget/   桌面小组件（样式、布局拼装、集合部件服务）
└── ui/       Compose 界面：主题、课表、今日、设置、通用组件
```

## 隐私

- 学号和密码只保存在本机 `SharedPreferences`，不加密上传、不写日志
- 验证码识别完全在本地推理，图片不出设备
- 除教务系统域名外不发起任何网络请求：没有统计 SDK、没有广告、没有崩溃上报
- 卸载应用即清除全部数据；应用内「清空本机数据」可手动清除

## 已知限制

- 解析规则是按广职大的 jsxsd 系统写的，换学校大概率要改 `JsxsdParser`
- 验证码识别率约 89%，赶上干扰线特别重的会失败，重新点一次即可
- 部分国产 ROM 会限制后台，提醒可能被吞，需要手动允许自启动 / 关闭电池优化
- 小组件在个别 Launcher 上覆盖升级后需要删掉重新添加
- 只支持 Android，没有 iOS 版本

## 第三方组件

| 组件 | 用途 | 协议 |
|---|---|---|
| [ddddocr](https://github.com/sml2h3/ddddocr) 的 `common_old.onnx` | 验证码识别模型 | MIT |
| [onnxruntime](https://github.com/microsoft/onnxruntime) | 端侧推理 | MIT |
| [AndroidX / Jetpack Compose](https://developer.android.com/jetpack) | UI 与基础库 | Apache-2.0 |

模型文件随源码一起分发，遵循其原始 MIT 协议。

## 参与贡献

欢迎提 Issue 和 PR。如果你也是广职大的同学，遇到解析失败、验证码识别出错，最有用的反馈是**附上一份脱敏后的课表页 HTML**（把姓名学号去掉），这样能直接复现。

## 开源协议

本项目以 **GNU General Public License v3.0** 发布，完整条款见 [LICENSE](LICENSE)。

简单说：你可以自由使用、修改、分发，但分发时必须以同样的协议开放源码，并保留原作者署名。

```
Copyright (C) 2026 Campsisian

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

## 作者

**Campsisian** — [github.com/Campsisian](https://github.com/Campsisian)

如果这个工具帮你少点了几次教务系统，给个 Star 就好。

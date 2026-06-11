# Clawd Mobile 🦀

原生 Android 桌宠伴侣 App，通过 LAN WebSocket 连接 [Clawd on Desk](https://github.com/rullerzhou-afk/clawd-on-desk)，实时监控 Claude Code 会话状态。

Clawd on Desk 自带 Telegram Bot 和 PWA 移动端预览，已经很好用了。Clawd Mobile 只是换了一种交互方式——把会话监控和审批做成原生 Android 应用，在通知和后台保活方面利用系统特性做了一点补充。

## ✨ 功能特性

- 📡 **实时会话监控** — 通过局域网 WebSocket 连接桌面端，实时显示所有 Claude Code 会话状态
- 🔔 **锁屏通知** — 任务完成、出错、需要关注时，即使手机锁屏也能收到通知
- 🛡️ **前台 Service 保活** — 后台持续运行，不会被系统杀掉
- ✅ **远程审批** — 在手机上批准/拒绝 Claude Code 的权限请求
- 🌙 **暗色主题** — 与 Clawd on Desk 桌面端一致的深色风格
- 📊 **事件时间线** — 点击会话卡片展开，查看详细事件流
- 🔄 **自动重连** — 网络断开后指数退避自动重连

## 💻 兼容性

- **桌面端**：需配合 [Clawd on Desk](https://github.com/rullerzhou-afk/clawd-on-desk) 使用
- **手机端**：Android 8.0 (API 26) 及以上
- **操作系统**：目前已测试 Windows 11 + Xiaomi 13 Ultra (HyperOS)
- 暂不支持 macOS / iOS

## 🛠 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin 1.9.24 |
| UI | Jetpack Compose + Material3 |
| DI | Hilt + KSP |
| 网络 | OkHttp 4.12 |
| JSON | Gson |
| 持久化 | DataStore Preferences |
| 构建 | Gradle 8.7 + Version Catalog |

## 📋 环境要求

- **JDK** 17 或以上
- **Android Studio** Hedgehog (2023.1) 或更新版本
- **Android** 8.0 (API 26) 及以上
- **Clawd on Desk** 桌面端已启动并开启移动端预览
- 手机与桌面端在**同一局域网**

## 🔧 构建

### 方式一：Android Studio（推荐）

1. 用 Android Studio 打开本项目目录
2. 等待 Gradle 同步完成
3. **Debug APK**：`Build > Build Bundle(s) / APK(s) > Build APK(s)`，生成未签名的调试包，可直接安装测试
4. **Release APK**：`Build > Generate Signed Bundle / APK > APK`，按向导创建或选择 Keystore 即可生成签名发布包

### 方式二：命令行

```bash
# 1. 克隆仓库
git clone https://github.com/ocspacius/clawd-mobile.git
cd clawd-mobile

# 2. 编译 Debug APK（无需签名，快速测试）
./gradlew assembleDebug

# 3. 编译 Release APK（需先配置签名）
./gradlew assembleRelease
```

> 命令行构建需确保 `JAVA_HOME` 指向 JDK 17+。Android Studio 自带 JDK（`<Android Studio 安装目录>/jbr`），可直接使用。

APK 输出路径：

| 构建类型 | 路径 |
|----------|------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release | `app/build/outputs/apk/release/app-release.apk` |

> **签名配置**：Release 构建需要签名。在项目根目录创建 `keystore.properties`：
> ```properties
> storeFile=your.keystore
> storePassword=your_store_password
> keyAlias=your_key_alias
> keyPassword=your_key_password
> ```
> 首次使用可用 Android Studio 的 `Generate Signed Bundle / APK` 向导创建 Keystore。详见 [Android 官方文档](https://developer.android.com/studio/publish/app-signing)。

## 📱 使用方式

### 桌面端

1. 打开 Clawd on Desk，进入**设置页面**
2. 开启 **「移动端预览」**，页面会显示 IP 地址、端口号和 Token
3. 保持桌面端运行，不要关闭

> 如果连接失败，请检查 Windows 防火墙是否放行了对应端口（通常需要在「允许应用通过防火墙」中放行 Clawd on Desk）。

### 手机端

1. 安装 APK，打开 Clawd Mobile
2. 切换到 **「设置」** Tab，填入桌面端显示的 IP 地址、端口和 Token
3. 点击 **「连接」**，顶部状态指示器变绿即为连接成功
4. 切换到 **「会话」** Tab，即可看到实时会话列表；点击卡片可展开事件时间线
5. 切到后台或锁屏后，App 通过前台 Service 保活，任务完成 / 出错时推送系统通知

## 🏗 项目结构

```
app/src/main/java/com/clawd/mobile/
├── ClawdApplication.kt          # Hilt Application
├── MainActivity.kt               # 单 Activity 宿主
├── di/                            # Hilt 依赖注入模块
├── data/
│   ├── model/                     # 数据模型 + 协议消息
│   ├── local/                     # DataStore 持久化
│   ├── websocket/                 # OkHttp WebSocket + 消息解析 + 重连策略
│   └── repository/                # 连接仓库 + 会话仓库 + 审批仓库
├── ui/
│   ├── theme/                     # 暗色主题 (Color / Type / Theme)
│   ├── navigation/                # 导航 (会话 ↔ 设置)
│   ├── components/                # 可复用组件
│   ├── sessions/                  # 会话列表页
│   ├── settings/                  # 设置页
│   └── approval/                  # 审批弹窗
└── service/
    ├── ClawdForegroundService.kt  # 前台 Service
    └── NotificationHelper.kt      # 通知管理
```

## 📄 协议

Clawd Mobile 通过 WebSocket 与桌面端通信，协议版本 `v1`：

| 消息类型 | 方向 | 说明 |
|----------|------|------|
| `snapshot` | Server → Client | 全量会话快照 |
| `state` | Server → Client | 单个会话状态更新 |
| `session_deleted` | Server → Client | 会话被删除 |
| `approval_request` | Server → Client | 远程权限审批请求 |
| `approval_response` | Client → Server | 审批回复 (allow/deny) |

## 📜 许可证

[MIT License](LICENSE) © 2026 Ocspacius

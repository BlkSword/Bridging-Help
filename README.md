# BridgingHelp - Android远程桌面协助应用

一款高性能、高流畅性的原生Android远程桌面协助应用，使用Kotlin和Jetpack Compose开发。

## 项目概述

BridgingHelp允许用户通过Android设备进行远程桌面协助，支持双向通信和控制。

### 主要功能

- **屏幕捕获与共享** - 使用MediaProjection API进行高质量屏幕捕获
- **远程输入控制** - 通过AccessibilityService实现触摸和键盘输入注入
- **P2P连接** - 基于WebRTC的点对点连接，支持NAT穿透
- **自适应质量** - 根据网络状况自动调整视频质量
- **双角色支持** - 支持控制端和受控端两种角色

## 技术栈

### 核心技术
- **Kotlin** - 2.0.21
- **Jetpack Compose** - 现代化声明式UI
- **Hilt** - 依赖注入
- **Coroutines & Flow** - 异步编程
- **WebRTC** - P2P通信
- **Material 3** - UI设计系统

### 关键库
- WebRTC (Google)
- OkHttp
- Kotlinx Serialization
- Navigation Compose

## 项目结构

```
Bridging-Help/
├── app/                          # 主应用模块
│   ├── src/main/
│   │   ├── java/com/bridginghelp/app/
│   │   │   ├── MainActivity.kt   # 主Activity
│   │   │   ├── BridgingHelpApp.kt # Application类
│   │   │   ├── di/               # Hilt依赖注入模块
│   │   │   ├── navigation/       # 导航配置
│   │   │   ├── ui/               # UI界面
│   │   │   │   ├── controller/   # 控制端UI
│   │   │   │   ├── controlled/   # 受控端UI
│   │   │   │   └── role/         # 角色选择
│   │   └── res/                  # 资源文件
│   └── build.gradle.kts
│
├── core/                         # 核心共享模块
│   ├── model/                    # 领域模型
│   │   ├── RemoteEvent.kt       # 远程事件（触摸、键盘）
│   │   ├── SessionState.kt      # 会话状态
│   │   ├── DeviceInfo.kt        # 设备信息
│   │   ├── VideoConfig.kt       # 视频配置
│   │   └── NetworkMetrics.kt    # 网络指标
│   ├── common/                   # 通用工具
│   │   ├── result/              # Result封装
│   │   ├── dispatcher/          # 协程调度器
│   │   ├── network/             # 网络观察器
│   │   └── util/                # 工具类
│   ├── permissions/             # 权限管理
│   │   ├── PermissionManager.kt
│   │   ├── MediaProjectionPermissionHandler.kt
│   │   └── AccessibilityPermissionHandler.kt
│   └── network/                 # 网络抽象
│
├── feature/                      # 功能模块
│   ├── capture/                 # 屏幕捕获模块
│   │   ├── service/            # ScreenCaptureService
│   │   ├── manager/            # ScreenCaptureManager
│   │   ├── encoder/            # VideoEncoder
│   │   └── quality/            # QualityMonitor
│   ├── injection/               # 输入注入模块
│   │   ├── service/            # RemoteInputService
│   │   └── injector/           # TouchInjector, KeyboardInjector
│   ├── webrtc/                  # WebRTC通信模块
│   │   ├── factory/            # WebRtcPeerConnectionFactory
│   │   ├── peer/               # ManagedPeerConnection
│   │   └── datachannel/        # DataChannelManager
│   └── signaling/               # 信令模块
│       ├── client/             # WebSocketSignalingClient
│       └── session/            # SessionManager
│
├── ui/                          # UI模块
│   └── compose/                 # Compose组件
│       ├── theme/              # 主题系统
│       └── components/         # 通用组件
│
├── gradle/
│   └── libs.versions.toml      # 版本目录
├── build.gradle.kts            # 项目级构建配置
└── settings.gradle.kts         # 模块配置
```

## 当前状态

### ✅ 已完成

#### 核心架构
- [x] 多模块Gradle项目结构
- [x] 版本目录（libs.versions.toml）依赖管理
- [x] Hilt依赖注入配置

#### 领域模型 (core/model)
- [x] RemoteEvent - 触摸、键盘、滚动事件
- [x] SessionState - 会话状态管理
- [x] DeviceInfo - 设备信息和能力
- [x] VideoConfig - 视频配置和编解码
- [x] NetworkMetrics - 网络质量指标
- [x] SignalingMessage - 信令协议

#### 通用工具 (core/common)
- [x] Result结果封装
- [x] 协程调度器提供者
- [x] 网络状态观察器
- [x] 日志系统
- [x] 扩展函数库

#### 权限管理 (core/permissions)
- [x] 基础权限管理器
- [x] MediaProjection权限处理器
- [x] Accessibility权限处理器

#### 屏幕捕获 (feature/capture)
- [x] ScreenCaptureService - 前台服务
- [x] ScreenCaptureManager - 捕获管理器
- [x] HardwareVideoEncoder - 硬件编码器
- [x] QualityMonitor - 质量监控

#### 输入注入 (feature/injection)
- [x] RemoteInputService - 无障碍服务
- [x] TouchInjector - 触摸注入器
- [x] KeyboardInjector - 键盘注入器

#### WebRTC通信 (feature/webrtc)
- [x] WebRtcPeerConnectionFactory - 工厂类
- [x] ManagedPeerConnection - 连接包装器
- [x] DataChannelManager - 数据通道管理

#### 信令 (feature/signaling)
- [x] WebSocketSignalingClient - WebSocket客户端
- [x] SessionManager - 会话管理器
- [x] SignalingMessage - 消息序列化

#### UI界面
- [x] Material 3主题系统
- [x] 连接状态指示器
- [x] 权限请求卡片
- [x] 加载动画组件
- [x] 角色选择界面
- [x] 控制端主页
- [x] 受控端主页
- [x] 导航框架

### 🚧 待完善

#### 功能完善
- [ ] 设备发现机制
- [ ] 完整的远程控制界面
- [ ] 文件传输功能
- [ ] 音频传输
- [ ] 剪贴板同步

#### 优化
- [ ] 自适应码率算法优化
- [ ] 编码器参数调优
- [ ] 内存优化
- [ ] 电量优化

#### 测试
- [ ] 单元测试
- [ ] 集成测试
- [ ] E2E测试
- [ ] 性能测试

## 构建说明

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 35
- Gradle 8.5+

### 构建步骤

1. 克隆仓库
```bash
git clone <repository-url>
cd Bridging-Help
```

2. 使用Android Studio打开项目

3. 同步Gradle

4. 运行应用
```bash
./gradlew installDebug
```

或直接在Android Studio中点击Run按钮

## 架构设计

### 架构模式
- **MVI (Model-View-Intent)** - 单向数据流
- **模块化** - 高内聚低耦合的模块设计
- **依赖注入** - 使用Hilt进行依赖管理

### 数据流
```
用户输入 -> UI -> ViewModel -> UseCase -> Repository -> DataSource
     ↓                                                    ↓
UI更新 <- State                                           Data
```

## 权限说明

应用需要以下权限：

| 权限 | 用途 |
|------|------|
| INTERNET | 网络通信 |
| FOREGROUND_SERVICE | 前台服务 |
| POST_NOTIFICATIONS | 显示通知 |
| WAKE_LOCK | 保持CPU运行 |
| MEDIA_PROJECTION | 屏幕捕获（特殊权限） |
| ACCESSIBILITY_SERVICE | 输入注入（特殊权限） |

## 性能目标

| 指标 | 目标值 |
|------|--------|
| 端到端延迟 | < 200ms |
| 帧率 | 30fps (可降至15fps自适应) |
| CPU使用率 | < 50% |
| 内存使用 | < 300MB |
| APK大小 | < 20MB |

## 使用说明

### 控制端（控制其他设备）
1. 启动应用，选择"控制端"
2. 等待发现可用设备（或手动输入设备ID）
3. 点击"连接"按钮
4. 等待受控端接受连接请求
5. 开始远程控制

### 受控端（被其他设备控制）
1. 启动应用，选择"受控端"
2. 确保无障碍服务已启用
3. 点击"开始会话"
4. 等待控制端连接
5. 接受连接请求后开始屏幕共享

## 贡献指南

1. Fork本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启Pull Request

## 许可证

[MIT License](LICENSE)

## 致谢

- [WebRTC](https://webrtc.org/) - Web实时通信
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 现代化UI工具包
- [Hilt](https://dagger.dev/hilt/) - Android依赖注入

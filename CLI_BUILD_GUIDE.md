# BridgingHelp 命令行编译指南

## 环境准备

### 方案一：仅使用命令行（推荐用于CI/CD）

#### 1. 安装JDK 17
```bash
# Windows (使用Chocolatey)
choco install openjdk17

# Linux
sudo apt install openjdk-17-jdk

# macOS
brew install openjdk@17
```

#### 2. 安装Android SDK命令行工具

**下载SDK命令行工具:**
- 访问: https://developer.android.com/studio#command-tools
- 下载 "Command line tools only"

**Windows:**
```bash
# 解压到指定目录，例如 C:\android-sdk
# 设置环境变量
setx ANDROID_SDK_ROOT "C:\android-sdk"
setx ANDROID_HOME "C:\android-sdk"

# 添加到PATH
setx PATH "%PATH%;C:\android-sdk\cmdline-tools\latest\bin"
setx PATH "%PATH%;C:\android-sdk\platform-tools"
```

**Linux/macOS:**
```bash
# 解压并移动到 ~/Android/sdk
unzip commandlinetools-linux-*.zip
mkdir -p ~/Android/sdk/cmdline-tools
mv cmdline-tools/latest ~/Android/sdk/cmdline-tools/

# 设置环境变量（添加到 ~/.bashrc 或 ~/.zshrc）
export ANDROID_SDK_ROOT="$HOME/Android/sdk"
export ANDROID_HOME="$HOME/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
export PATH="$PATH:$ANDROID_HOME/platform-tools"

# 使环境变量生效
source ~/.bashrc  # 或 source ~/.zshrc
```

#### 3. 接受许可并安装必要组件
```bash
# 更新SDK工具
yes | sdkmanager --licenses

# 安装必要组件
sdkmanager "platform-tools"
sdkmanager "platforms;android-35"
sdkmanager "build-tools;34.0.0"
sdkmanager "ndk;25.2.9519653"
```

### 方案二：安装Android Studio后使用命令行

如果你已安装Android Studio，SDK已经配置好了，直接用命令行即可：

```bash
# Android Studio自带的Gradle
# Windows
cd Bridging-Help
.\gradlew.bat build

# Linux/macOS
cd Bridging-Help
./gradlew build
```

## 命令行编译操作

### 基本编译命令

```bash
# 进入项目目录
cd Bridging-Help

# 清理构建
./gradlew clean

# 编译Debug版本
./gradlew assembleDebug

# 编译Release版本
./gradlew assembleRelease

# 运行测试
./gradlew test

# 构建并运行单元测试
./gradlew check
```

### 连接设备并安装

```bash
# 检查连接的设备
adb devices

# 安装Debug APK到设备
./gradlew installDebug

# 卸载应用
./gradlew uninstallDebug

# 清理并重新安装
./gradlew clean installDebug
```

### 使用仿真器

```bash
# 列出已创建的仿真器
emulator -list-avds

# 启动仿真器
emulator -avd Pixel_5_API_35

# 或使用avdmanager
avdmanager list avd
```

## 完整编译和运行流程

### Windows PowerShell
```powershell
# 1. 进入项目目录
cd D:\project\Bridging-Help

# 2. 清理旧构建
.\gradlew.bat clean

# 3. 编译Debug版本
.\gradlew.bat assembleDebug

# 4. 连接设备或启动仿真器
adb devices

# 5. 安装APK
.\gradlew.bat installDebug

# 6. 启动应用（可选）
adb shell am start -n com.bridginghelp.app/.MainActivity
```

### Linux/macOS
```bash
# 1. 进入项目目录
cd Bridging-Help

# 2. 清理旧构建
./gradlew clean

# 3. 编译Debug版本
./gradlew assembleDebug

# 4. 连接设备或启动仿真器
adb devices

# 5. 安装APK
./gradlew installDebug

# 6. 启动应用（可选）
adb shell am start -n com.bridginghelp.app/.MainActivity
```

## 调试选项

### 查看日志
```bash
# 查看应用日志
adb logcat | findstr BridgingHelp  # Windows
adb logcat | grep BridgingHelp     # Linux/macOS

# 清除日志
adb logcat -c
```

### 调试构建
```bash
# 编译Debug版本（包含调试信息）
./gradlew assembleDebug

# 安装Debug版本
./gradlew installDebug

# 附加调试器
adb forward tcp:5005 tcp:5005
```

## 常见问题

### 1. Gradle守护进程问题
```bash
# 停止所有Gradle守护进程
./gradlew --stop

# 使用单线程编译（避免并发问题）
./gradlew assembleDebug --no-daemon
```

### 2. 内存不足
```bash
# 在 gradle.properties 中增加内存
# 或使用命令行参数
./gradlew assembleDebug -Dorg.gradle.jvmargs="-Xmx4096m"
```

### 3. 找不到ANDROID_SDK_ROOT
```bash
# Windows PowerShell
$env:ANDROID_SDK_ROOT = "C:\Users\YourName\AppData\Local\Android\Sdk"

# Linux/macOS
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
```

### 4. 许证未接受
```bash
# 接受所有许可
yes | sdkmanager --licenses
```

## CI/CD 集成示例

### GitHub Actions
```yaml
name: Build Android

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v2

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build with Gradle
        run: ./gradlew build

      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
```

### GitLab CI
```yaml
image: openjdk:17-jdk

before_script:
  - apt-get --quiet update --yes
  - apt-get --quiet install --yes wget tar unzip lib32std++6 lib32z1
  - wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
  - unzip -q commandlinetools-linux-*.zip -d $ANDROID_SDK_ROOT/cmdline-tools
  - export PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools
  - yes | sdkmanager --licenses
  - sdkmanager --update
  - sdkmanager "platform-tools" "platforms;android-35" "build-tools;34.0.0"
  - chmod +x gradlew

build:
  script:
    - ./gradlew assembleDebug
  artifacts:
    paths:
      - app/build/outputs/apk/debug/*.apk
```

## 总结

**不需要Android Studio**，只需要：
1. JDK 17
2. Android SDK命令行工具
3. Gradle（项目自带）

**Android Studio的优势**：
- 图形化界面设计器
- 可视化调试工具
- 代码补全和智能提示
- APK分析工具
- 性能分析工具

**命令行的优势**：
- 适合自动化和CI/CD
- 更轻量级
- 可以在任何服务器上运行
- 更容易脚本化

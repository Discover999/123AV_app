# 123AV Android 应用

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.02.00-yellow.svg)](https://developer.android.com/jetpack/compose)
[![Android SDK](https://img.shields.io/badge/Android%20SDK-34-green.svg)](https://developer.android.com/studio)

一个基于 Android 平台的视频聚合应用，提供视频浏览、播放、搜索和个人中心功能。采用纯 Kotlin + Jetpack Compose 现代Android开发技术栈。

## 功能特性

| 模块 | 功能描述 |
|------|----------|
| **首页** | 推荐视频展示，支持分类切换和下拉刷新 |
| **搜索** | 关键词搜索视频内容 |
| **收藏** | 用户登录后管理个人收藏视频，支持分页加载 |
| **个人中心** | 用户信息展示、登录状态管理 |

### 核心功能

- **视频播放**：在线视频播放，独立播放器界面
- **分页加载**：无限滚动分页，提升大数据量加载性能
- **状态持久化**：用户登录状态本地管理
- **响应式UI**：Material Design 3 设计规范

## 技术架构

### 技术栈

| 类别 | 技术选型 |
|------|----------|
| 开发语言 | Kotlin |
| UI框架 | Jetpack Compose |
| 状态管理 | MutableState + StateFlow |
| 网络请求 | OkHttp + Retrofit |
| HTML解析 | Jsoup |
| 异步处理 | Coroutines |
| 最低SDK | 24 (Android 7.0) |
| 目标SDK | 34 (Android 14) |

### 项目结构

```
app/src/main/java/com/android123av/app/
├── components/          # 可复用UI组件
│   ├── VideoItem.kt     # 视频列表项
│   ├── PaginationComponent.kt  # 分页组件
│   └── LoadingSkeleton.kt # 加载骨架屏
├── models/              # 数据模型
│   └── Video.kt         # 视频数据模型
├── network/             # 网络层
│   ├── NetworkService.kt  # 网络请求服务
│   └── ApiEndpoints.kt    # API接口定义
├── screens/             # 页面组件
│   ├── HomeScreen.kt    # 首页
│   ├── FavoritesScreen.kt  # 收藏页面
│   ├── ProfileScreen.kt  # 个人中心
│   └── SearchScreen.kt  # 搜索页面
├── state/               # 状态管理
│   └── UserStateManager.kt  # 用户状态管理
├── ui/theme/            # 主题配置
│   ├── Theme.kt         # 应用主题
│   ├── Color.kt         # 颜色定义
│   └── Type.kt          # 字体排版
├── MainActivity.kt      # 应用入口
└── Navigation.kt        # 导航配置
```

## 环境配置

### 开发环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Gradle 8.4+
- Android SDK 34

### 依赖配置

项目使用 Gradle Kotlin DSL 进行构建配置，主要依赖包括：

```kotlin
// Core
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

// Compose BOM
implementation(platform("androidx.compose:compose-bom:2024.02.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")

// Navigation
implementation("androidx.navigation:navigation-compose:2.7.7")

// Network
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("org.jsoup:jsoup:1.17.2")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

## 快速开始

### 编译构建

```bash
# 同步并构建项目
./gradlew assembleDebug

# 运行 lint 检查
./gradlew lint

# 运行单元测试
./gradlew test

# 清理构建产物
./gradlew clean

# 调试构建
./gradlew installDebug
```

### 运行应用

1. 使用 Android Studio 打开项目
2. 等待 Gradle 同步完成
3. 选择目标设备（真机或模拟器）
4. 点击 `Run` 按钮或使用快捷键 `Shift + F10`

## 数据来源

本应用数据来源于 [123av.com](https://123av.com)，仅供学习和研究使用。

## 许可证

MIT License

---

> **声明**：本项目仅供学习交流使用，请勿用于商业用途。如有侵权，请联系删除。

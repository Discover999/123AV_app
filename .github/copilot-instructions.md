# Copilot 使用说明（项目：123AV_app）

本文件为 AI 编码代理提供即时可用的项目上下文、约定和常用命令，便于高效修改与维护代码。

## 1) 项目架构
- **开发语言**：Kotlin 2.0.21
- **UI 框架**：Jetpack Compose（组合式 UI）
- **主包路径**：[app/src/main/java/com/android123av/app](app/src/main/java/com/android123av/app)
- **构建工具**：Gradle 8.13.2 + Kotlin DSL + 版本目录（`libs.versions.toml`）
- **最低 SDK**：Android 11 (API 30)
- **目标 SDK**：Android 14 (API 36)
- **视频播放**：AndroidX Media3 (ExoPlayer) 1.3.1
- **本地存储**：Room Database 2.8.4

## 2) 关键组件与数据流

### UI 层
- **screens/**：主要页面组件
  - [HomeScreen.kt](app/src/main/java/com/android123av/app/screens/HomeScreen.kt) - 首页，包含视频列表、搜索、分类切换
  - [VideoPlayerScreen.kt](app/src/main/java/com/android123av/app/screens/VideoPlayerScreen.kt) - 视频播放页面
  - [FavoritesScreen.kt](app/src/main/java/com/android123av/app/screens/FavoritesScreen.kt) - 收藏页面
  - [DownloadsScreen.kt](app/src/main/java/com/android123av/app/screens/DownloadsScreen.kt) - 下载页面
  - [ProfileScreen.kt](app/src/main/java/com/android123av/app/screens/ProfileScreen.kt) - 个人中心
  - [SettingsScreen.kt](app/src/main/java/com/android123av/app/screens/SettingsScreen.kt) - 设置页面
  - [HelpScreen.kt](app/src/main/java/com/android123av/app/screens/HelpScreen.kt) - 帮助页面

### 网络层
- **network/**：网络请求与解析
  - [NetworkService.kt](app/src/main/java/com/android123av/app/network/NetworkService.kt) - 网络请求服务，包含 LRU 缓存（50 项，30 分钟过期）
  - [HtmlParser.kt](app/src/main/java/com/android123av/app/network/HtmlParser.kt) - HTML 解析器
  - [SiteManager.kt](app/src/main/java/com/android123av/app/network/SiteManager.kt) - 站点管理
  - [PersistentCookieJar.kt](app/src/main/java/com/android123av/app/network/PersistentCookieJar.kt) - Cookie 持久化

### 数据模型
- **models/**：数据模型
  - [Models.kt](app/src/main/java/com/android123av/app/models/Models.kt) - 视频数据模型
  - [VideoDetails.kt](app/src/main/java/com/android123av/app/models/VideoDetails.kt) - 视频详情模型
  - [PlayerState.kt](app/src/main/java/com/android123av/app/models/PlayerState.kt) - 播放器状态

### 视频播放
- **player/**：播放器管理
  - [ExoPlayerManager.kt](app/src/main/java/com/android123av/app/player/ExoPlayerManager.kt) - ExoPlayer 管理器
- **viewmodel/**：播放器 ViewModel
  - [VideoPlayerViewModel.kt](app/src/main/java/com/android123av/app/viewmodel/VideoPlayerViewModel.kt) - 播放器 ViewModel
  - [VideoPlayerViewModelFactory.kt](app/src/main/java/com/android123av/app/viewmodel/VideoPlayerViewModelFactory.kt) - ViewModel 工厂

### 下载管理
- **download/**：下载管理
  - [M3U8DownloadManager.kt](app/src/main/java/com/android123av/app/download/M3U8DownloadManager.kt) - M3U8 下载管理器，支持多线程下载、AES-128 解密
  - [DownloadDatabase.kt](app/src/main/java/com/android123av/app/download/DownloadDatabase.kt) - 下载数据库
  - [DownloadModels.kt](app/src/main/java/com/android123av/app/download/DownloadModels.kt) - 下载任务模型
  - [CachedVideoDetails.kt](app/src/main/java/com/android123av/app/download/CachedVideoDetails.kt) - 缓存视频详情
  - [VideoDetailsCacheManager.kt](app/src/main/java/com/android123av/app/download/VideoDetailsCacheManager.kt) - 视频详情缓存管理

### 状态管理
- **state/**：状态管理
  - [UserStateManager.kt](app/src/main/java/com/android123av/app/state/UserStateManager.kt) - 用户状态管理
  - [AppState.kt](app/src/main/java/com/android123av/app/state/AppState.kt) - 应用状态
  - [DownloadPathManager.kt](app/src/main/java/com/android123av/app/state/DownloadPathManager.kt) - 下载路径管理
  - [SearchHistoryManager.kt](app/src/main/java/com/android123av/app/state/SearchHistoryManager.kt) - 搜索历史管理
  - [ThemeStateManager.kt](app/src/main/java/com/android123av/app/state/ThemeStateManager.kt) - 主题状态管理

### UI 组件
- **components/**：可复用 UI 组件
  - [CategoryTabs.kt](app/src/main/java/com/android123av/app/components/CategoryTabs.kt) - 分类标签组件
  - [PaginationComponent.kt](app/src/main/java/com/android123av/app/components/PaginationComponent.kt) - 分页组件
  - [VideoItem.kt](app/src/main/java/com/android123av/app/components/VideoItem.kt) - 视频列表项组件
  - [NavigationComponent.kt](app/src/main/java/com/android123av/app/components/NavigationComponent.kt) - 导航组件
- **ui/components/**：UI 子组件
  - [PlayerControls.kt](app/src/main/java/com/android123av/app/ui/components/PlayerControls.kt) - 播放器控件
  - [VideoInfoPanel.kt](app/src/main/java/com/android123av/app/ui/components/VideoInfoPanel.kt) - 视频信息面板
  - [LoadingState.kt](app/src/main/java/com/android123av/app/ui/components/LoadingState.kt) - 加载状态
  - [VideoErrorState.kt](app/src/main/java/com/android123av/app/ui/components/VideoErrorState.kt) - 视频错误状态
- **ui/theme/**：主题配置
  - [Theme.kt](app/src/main/java/com/android123av/app/ui/theme/Theme.kt) - 应用主题
  - [Color.kt](app/src/main/java/com/android123av/app/ui/theme/Color.kt) - 颜色定义
  - [Type.kt](app/src/main/java/com/android123av/app/ui/theme/Type.kt) - 字体排版

## 3) 项目约定（重要，勿随意更改）

### Compose 状态管理
- 屏幕内大量使用 `mutableStateOf` / `StateFlow`，避免在非 UI 线程直接修改 Compose state
- 使用 `rememberCoroutineScope()` 与 `LaunchedEffect` 协调异步操作
- 使用 `DisposableEffect` 管理资源释放（如 ExoPlayer）

### 依赖管理
- 依赖通过版本目录引用（`libs.versions.toml`），修改依赖请同步更新该文件
- 使用 KSP（Kotlin Symbol Processing）处理 Room 数据库注解

### 视频播放
- 对 HLS (`.m3u8`) 有特殊处理（`createMediaSource`）
- 支持本地文件路径播放（`localVideoPath` 参数）
- 修改播放器请保持 `DisposableEffect` 中的 `exoPlayer.release()`
- 使用 Media3 (ExoPlayer) 1.3.1 版本

### 日志与错误处理
- 代码内广泛使用 `Log.d`/`Log.e` 与 `Toast`
- UI 交互在播放错误时恢复控件可用性

### 网络请求
- 使用 OkHttp 4.12.0 进行网络请求
- 使用 Jsoup 1.17.2 进行 HTML 解析
- 使用 Gson 2.10.1 进行 JSON 解析
- 网络层初始化在 `MainActivity` 调用 `initializeNetworkService(context)`

## 4) 构建、测试与调试（快速命令）

```bash
# 同步并构建（Debug）
./gradlew assembleDebug

# 安装到设备（Debug）
./gradlew installDebug

# 运行单元测试
./gradlew test

# lint 检查
./gradlew lint

# 清理构建产物
./gradlew clean
```

## 5) 编辑/修改建议（Agent 指南）

### 小步提交
- 优先做小范围改动（单一屏幕、单一模块）
- 确保不引入全局构建改动（如 JDK/Gradle 版本）

### UI 状态与协程
- 修改 UI 状态或协程逻辑时，搜索 `LaunchedEffect` / `DisposableEffect` / `rememberCoroutineScope`
- 保证生命周期正确处理

### 播放器修改
- 修改播放器相关代码时，注意 Media3 的线程与生命周期
- 保留错误处理分支（`onPlayerError`）以免退化用户体验
- 确保 ExoPlayer 在 `DisposableEffect` 中正确释放

### 依赖版本变更
- 变更第三方库版本前，先检查 `build.gradle.kts` 与 `libs.versions.toml`
- 在 CI/本地运行 `./gradlew assembleDebug` 验证

## 6) 常见改动例子

### 播放本地文件测试
在导航或 Activity/Preview 中调用：
```kotlin
VideoPlayerScreen(
    localVideoPath = "/path/to/file.mp4",
    onBack = { /*...*/ }
)
```

### 扩展下载功能
查看 `download/M3U8DownloadManager` 与 `state/DownloadPathManager`（状态与保存路径约定）

### 修改搜索功能
查看 `screens/HomeScreen.kt` 中的搜索对话框实现和 `state/SearchHistoryManager.kt`

## 7) 重要路径

- **应用入口**：[app/src/main/java/com/android123av/app/MainActivity.kt](app/src/main/java/com/android123av/app/MainActivity.kt)
- **导航配置**：[app/src/main/java/com/android123av/app/Navigation.kt](app/src/main/java/com/android123av/app/Navigation.kt)
- **播放器视图与控件**：[app/src/main/java/com/android123av/app/screens/VideoPlayerScreen.kt](app/src/main/java/com/android123av/app/screens/VideoPlayerScreen.kt)
- **构建配置**：`build.gradle.kts`（根）与 `app/build.gradle.kts`（模块）
- **版本目录**：`gradle/libs.versions.toml`

## 8) 限制与注意事项

- 本仓库数据来源为第三方站点（README 中注明），请避免将爬虫/爬取逻辑公开推送到对外服务或生产环境
- 最低 SDK 与编译 SDK 在 `app/build.gradle.kts` 指定（minSdk 30，targetSdk 36），避免随意降级
- 使用 KSP 处理 Room 数据库注解，不要使用 kapt

## 9) 实现细节（重要）

### 视频地址缓存
- `NetworkService` 使用一个有限大小的 LRU 风格 `LinkedHashMap` 缓存（50 项，过期时间 30 分钟）
- 通过 `getCachedVideoUrl` / `cacheVideoUrl` 访问

### 并行获取策略
- `fetchVideoUrlParallel` 并行尝试 HTTP 解析（Jsoup + 脚本解析）和 WebView 拦截
- 使用 `select` 取第一个返回结果，超时或失败时回退到顺序尝试

### WebView 拦截
- `fetchM3u8UrlWithWebViewFast` 在主线程创建 `WebView`
- 通过 `shouldInterceptRequest` 拦截 `.m3u8`/`.mp4`/`.mpd` 请求来获取真实播放 URL（默认超时 5s）

### 网络层初始化
- 在 `MainActivity` 调用 `initializeNetworkService(context)`
- 该方法创建带有 `PersistentCookieJar` 的 `OkHttpClient`（内建 50MB 缓存、超时、连接池与重试策略）

### 下载管理
- `M3U8DownloadManager` 使用 Room 数据库保存任务
- 暴露 `startDownload` / `pauseDownload` / `resumeDownload` / `cancelDownload`
- 提供 `observeTaskById`（返回 `Flow<DownloadTask?>`）供 UI 订阅

### 并发下载策略
- 多线程下载时根据分段数计算线程数（默认 4，最大 8）
- 对较少分段使用单线程以减少开销
- 进度与速率通过平滑算法更新数据库以减少 UI 抖动

### 加密与合并
- 支持 AES-128 key 下载（提取 `#EXT-X-KEY`）
- 单段下载后可解密并在完成后合并成 `video.mp4`

### 下载路径
- 由 `DownloadPathManager` 管理
- 支持自定义路径（保存在 SharedPreferences）
- 默认路径指向 `context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)/123AV_Downloads`

### 图片加载
- 使用 Coil 2.7.0 进行图片加载
- 支持网络图片和本地图片

### 搜索历史
- 由 `SearchHistoryManager` 管理
- 支持添加、删除、清空搜索历史
- 使用 SharedPreferences 持久化存储

### 主题管理
- 由 `ThemeStateManager` 管理
- 支持浅色/深色主题切换
- 使用 SharedPreferences 持久化存储

### 用户状态
- 由 `UserStateManager` 管理
- 支持登录/登出状态管理
- 使用 SharedPreferences 持久化存储

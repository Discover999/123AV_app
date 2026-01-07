# Copilot 使用说明（项目：123AV_app）

本文件为 AI 编码代理提供即时可用的项目上下文、约定和常用命令，便于高效修改与维护代码。

1) 大致架构
- Kotlin + Jetpack Compose 前端（组合式 UI）；主包路径：[app/src/main/java/com/android123av/app](app/src/main/java/com/android123av/app)
- 单一模块 Android 应用（`app` 模块），使用 Gradle Kotlin DSL 与版本目录（`libs.versions.toml`）。
- 视频播放使用 AndroidX Media3（ExoPlayer）与自定义下载管理（见 `download` 包）。示例：[app/src/main/java/com/android123av/app/screens/VideoPlayerScreen.kt](app/src/main/java/com/android123av/app/screens/VideoPlayerScreen.kt)

2) 关键组件与数据流
- UI 层：`screens/`（例如 `HomeScreen`, `SearchScreen`, `VideoPlayerScreen`）以 Compose 组件为主。
- 网络层：`network/` 提供请求与解析（OkHttp / Jsoup / Gson）。优先查看 `NetworkService.kt` 与 `ApiEndpoints.kt`。
- 数据模型：`models/`（例如 `Video.kt`, `VideoDetails`）；播放器先从 `Video` 缓存或通过 `fetchM3u8UrlWithWebView` 获取 m3u8 地址，再通过 Media3 播放。
- 下载：`download/` 包含 `M3U8DownloadManager` 与任务模型；编辑下载行为请参考该目录实现细节与状态流。

3) 项目约定（重要，勿随意更改）
- Compose 状态：屏幕内大量使用 `mutableStateOf` / `StateFlow`，避免在非 UI 线程直接修改 Compose state；使用 `rememberCoroutineScope()` 与 `LaunchedEffect` 协调异步。
- 依赖通过版本目录引用（`libs`），修改依赖请同步更新 `gradle/libs.versions.toml` 与 `build.gradle.kts`。
- 视频播放：对 HLS (`.m3u8`) 有特殊处理（`createMediaSource`），以及对本地文件路径支持（`localVideoPath` 参数）。修改播放器请保持 `DisposableEffect` 中的 `exoPlayer.release()`。
- 日志/错误：代码内广泛使用 `Log.d`/`Log.e` 与 `Toast`，UI 交互在播放错误时恢复控件可用性。

4) 构建、测试与调试（快速命令）
- 同步并构建（Debug）：`./gradlew assembleDebug`
- 安装到设备（Debug）：`./gradlew installDebug`
- 运行单元测试：`./gradlew test`
- lint 检查：`./gradlew lint`
- 清理：`./gradlew clean`

5) 编辑/修改建议（Agent 指南）
- 小步提交：优先做小范围改动（单一屏幕、单一模块），确保不引入全局构建改动（如 JDK/Gradle 版本）。
- 修改 UI 状态或协程逻辑时，搜索 `LaunchedEffect` / `DisposableEffect` / `rememberCoroutineScope`，保证生命周期正确处理。
- 修改播放器相关代码时，注意 Media3 的线程与生命周期，保留错误处理分支（`onPlayerError`）以免退化用户体验。
- 变更第三方库版本前，先检查 `build.gradle.kts` 与 `libs.versions.toml`，并在 CI/本地运行 `./gradlew assembleDebug` 验证。

6) 常见改动例子
- 若需在播放页面传入本地文件测试：在导航或 Activity/Preview 中调用 `VideoPlayerScreen(localVideoPath = "/path/to/file.mp4", onBack = { /*...*/ })`，示例文件在 [app/src/main/java/com/android123av/app/screens/VideoPlayerScreen.kt](app/src/main/java/com/android123av/app/screens/VideoPlayerScreen.kt)。
- 若需扩展下载功能：查看 `download/M3U8DownloadManager` 与 `state/DownloadPathManager`（状态与保存路径约定）。

7) 重要路径
- 应用入口：[app/src/main/java/com/android123av/app/MainActivity.kt](app/src/main/java/com/android123av/app/MainActivity.kt)
- 导航：[app/src/main/java/com/android123av/app/Navigation.kt](app/src/main/java/com/android123av/app/Navigation.kt)
- 播放器视图与控件：[app/src/main/java/com/android123av/app/screens/VideoPlayerScreen.kt](app/src/main/java/com/android123av/app/screens/VideoPlayerScreen.kt)
- 构建配置：`build.gradle.kts`（根）与 `app/build.gradle.kts`（模块）

8) 限制与注意事项
- 本仓库数据来源为第三方站点（README 中注明），请避免将爬虫/爬取逻辑公开推送到对外服务或生产环境。
- 最低 SDK 与编译 SDK 在 `app/build.gradle.kts` 指定（minSdk 30，targetSdk 36），避免随意降级。

9) 实现细节（重要）
- 视频地址缓存：`NetworkService` 使用一个有限大小的 LRU 风格 `LinkedHashMap` 缓存（50 项，过期时间 30 分钟），通过 `getCachedVideoUrl` / `cacheVideoUrl` 访问。
- 并行获取策略：`fetchVideoUrlParallel` 并行尝试 HTTP 解析（Jsoup + 脚本解析）和 WebView 拦截，使用 `select` 取第一个返回结果，超时或失败时回退到顺序尝试。
- WebView 拦截：`fetchM3u8UrlWithWebViewFast` 在主线程创建 `WebView`，通过 `shouldInterceptRequest` 拦截 `.m3u8`/`.mp4`/`.mpd` 请求来获取真实播放 URL（默认超时 5s）。
- 网络层初始化：在 `MainActivity` 调用 `initializeNetworkService(context)`，该方法创建带有 `PersistentCookieJar` 的 `OkHttpClient`（内建 50MB 缓存、超时、连接池与重试策略）。
- 下载管理：`M3U8DownloadManager` 使用 Room 数据库保存任务，暴露 `startDownload` / `pauseDownload` / `resumeDownload` / `cancelDownload`，并提供 `observeTaskById`（返回 `Flow<DownloadTask?>`）供 UI 订阅。
- 并发下载策略：多线程下载时根据分段数计算线程数（默认 4，最大 8），对较少分段使用单线程以减少开销；进度与速率通过平滑算法更新数据库以减少 UI 抖动。
- 加密与合并：支持 AES-128 key 下载（提取 `#EXT-X-KEY`），单段下载后可解密并在完成后合并成 `video.mp4`。
- 下载路径：由 `DownloadPathManager` 管理，支持自定义路径（保存在 SharedPreferences），默认路径指向 `context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)/123AV_Downloads`。

请审阅此草案并指出需补充或不准确之处（例如缺失的关键文件或特殊运行流程），我会据此迭代更新。

# 123AV App

*** 项目采用 AI 自搓 ***
一个基于Android平台的视频应用，提供视频播放、搜索和个人中心功能。

## 功能特性

- **视频播放**：支持在线视频播放，使用独立的视频播放器界面
- **视频搜索**：可以通过关键词搜索视频内容
- **个人中心**：用户可以查看个人信息和收藏的视频
- **分类浏览**：支持按不同分类浏览视频内容

## 安装说明

1. 克隆或下载项目到本地
2. 使用Android Studio打开项目
3. 等待Gradle同步完成
4. 连接Android设备或启动模拟器
5. 点击运行按钮安装应用

## 使用方法

1. 打开应用后默认进入首页
2. 点击底部导航栏切换不同功能模块
3. 在首页可以浏览推荐视频
4. 在搜索页面可以输入关键词搜索视频
5. 在个人中心页面可以查看个人信息

## 技术细节

- **开发语言**：Kotlin
- **框架**：Jetpack Compose
- **包名**：com.android123av.app
- **应用名**：123AV
- **数据接口**：123av官网

## 项目结构

```
app/src/main/
├── java/com/android123av/app/
│   ├── components/        # UI组件
│   ├── models/            # 数据模型
│   ├── network/           # 网络服务
│   ├── screens/           # 页面组件
│   ├── state/             # 状态管理
│   ├── ui/theme/          # 主题配置
│   ├── video/             # 视频播放相关
│   ├── MainActivity.kt    # 主活动
│   ├── Navigation.kt      # 导航配置
│   └── VideoPlayerActivity.kt  # 视频播放活动
└── res/                   # 资源文件
    ├── drawable/          # 图像资源
    ├── mipmap-*/          # 应用图标
    └── values/            # 字符串、颜色等配置
```

## 构建命令

```bash
# 构建项目
./gradlew build

# 清理项目
./gradlew clean

# 安装应用到设备
./gradlew installDebug
```

## 许可证

MIT License

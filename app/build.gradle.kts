plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.android123av.app"
    compileSdk {
        version = release(36)
    }

    val keystoreProperties = file("keystore.properties").takeIf { it.exists() }?.readLines()?.associate { line ->
        val (key, value) = line.split("=", limit = 2)
        key.trim() to value.trim()
    } ?: emptyMap()

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = keystoreProperties["storePassword"] ?: System.getenv("KEY_STORE_PASSWORD") ?: ""
            keyAlias = keystoreProperties["keyAlias"] ?: System.getenv("KEY_ALIAS") ?: ""
            keyPassword = keystoreProperties["keyPassword"] ?: System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    defaultConfig {
        applicationId = "com.android123av.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
        checkDependencies = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // 网络请求相关依赖
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.google.code.gson:gson:2.10.1")

    // 图片加载库
    implementation(libs.coil.compose)

    // AndroidX Media3 视频播放库
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation(libs.androidx.compose.foundation)
    implementation(libs.play.services.cast)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.constraintlayout)

    // Room数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.common.jvm)
    implementation(libs.androidx.compose.remote.creation.core)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
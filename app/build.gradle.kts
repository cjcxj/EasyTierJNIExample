// app/build.gradle.kts

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.wire) // 应用 Wire 插件
    // id("org.jetbrains.kotlin.kapt") // 移除 Kapt 插件
}

android {
    namespace = "com.easytier.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.easytier.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // 修改 sourceSets，移除不标准的目录
    sourceSets {
        getByName("main") {
            // 使用我们找到的、包含 'debug' 的确切路径
            java.srcDir("build/generated/source/wire/debug")
        }
    }
}

wire {
    kotlin {
        // 添加下面这一行！
        rpcRole = "none"
    }
    sourcePath {
        srcDir("src/main/proto")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat) // 如果你还在用 AppCompat 的组件
    implementation(libs.androidx.constraintlayout) // 添加这一行

    // Moshi 和 Wire 的依赖
    implementation(libs.moshi)
    implementation(libs.wire.runtime)
    implementation(libs.wire.moshi.adapter)

    // 移除了 `kapt 'com.squareup.wire:wire-compiler:...'`

    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
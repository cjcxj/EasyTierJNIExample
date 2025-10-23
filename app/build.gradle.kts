// app/build.gradle.kts

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.wire)
    alias(libs.plugins.kotlinxSerialization)
}

android {
    namespace = "com.easytier.app"
    compileSdk = 36

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

    signingConfigs {
        create("release") {
            // 读取 gradle.properties 中的变量
            storeFile = file("E:/programming-language/android/EasyTierJNIExample/keystore.jks") // 使用绝对或相对路径
            storePassword = System.getenv("MY_KEYSTORE_PASSWORD") ?: property("MY_KEYSTORE_PASSWORD") as String
            keyAlias = System.getenv("MY_KEY_ALIAS") ?: property("MY_KEY_ALIAS") as String
            keyPassword = System.getenv("MY_KEY_PASSWORD") ?: property("MY_KEY_PASSWORD") as String
        }
    }

    buildTypes {
        release {
            // 【关键】启用代码缩减、混淆和优化
            isMinifyEnabled = true

            // 【推荐】启用资源缩减，移除未使用的资源
            isShrinkResources = true

            // ProGuard/R8 规则文件
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )

            // (可选，但推荐) 定义一个签名配置
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Debug构建通常不开启优化
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "EasyTierJNIExample (Debug)")
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

wire {
    kotlin {
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
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.wire.runtime)
    implementation(libs.wire.moshi.adapter)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.material.icons.extended)

    // Kotlinx Serialization 核心库
    implementation(libs.kotlinx.serialization.core)

    // Ktoml 库，用于处理 TOML 格式
    implementation(libs.ktoml.core)


}
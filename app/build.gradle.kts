plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.bridginghelp.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bridginghelp.remotehelp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // 签名配置
    signingConfigs {
        create("release") {
            // Release 签名配置（用于正式发布）
            // 将 keystore 文件放在 app/ 目录下，或使用环境变量
            storeFile = file("bridginghelp-release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "bridging123"
            keyAlias = System.getenv("KEY_ALIAS") ?: "release"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "bridging123"
        }
    }

    buildTypes {
        debug {
            // Debug 使用默认 debug keystore，由 Android SDK 自动提供
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 使用 release 签名配置
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Exclude duplicate annotation JARs
            excludes += "META-INF/annotations-12.0.jar"
            excludes += "META-INF/annotations-23.0.0.jar"
        }
        jniLibs {
            // 处理 WebRTC native 库冲突
            pickFirsts.add("lib/**/*.so")
        }
    }
}

// Force latest annotations version
configurations.all {
    resolutionStrategy {
        force("org.jetbrains:annotations:24.0.0")
    }
}

dependencies {
    // Core modules
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:permissions"))
    implementation(project(":core:discovery"))
    implementation(project(":core:filetransfer"))
    implementation(project(":core:audio"))
    implementation(project(":core:clipboard"))

    // Feature modules
    implementation(project(":feature:capture"))
    implementation(project(":feature:injection"))
    implementation(project(":feature:webrtc"))
    implementation(project(":feature:signaling"))

    // UI modules
    implementation(project(":ui:compose"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.androidx.compiler)
    implementation(libs.androidx.hilt.work)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // QR Code
    implementation(libs.zxing.android.embed)

    // Testing
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.ui.test.manifest)
}

kotlin {
    jvmToolchain(17)
}

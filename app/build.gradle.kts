plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.armx.chaoxing"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "com.armx.chaoxing"
        minSdk = 26
        targetSdk = 36
        versionCode = 146
        versionName = "8.1.16"

        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

        @Suppress("DEPRECATION")
        resourceConfigurations += listOf("zh-rCN", "en")
    }

    signingConfigs {
        create("release") {
            storeFile = file("cx-release.jks")
            storePassword = "cx123456"
            keyAlias = "cx"
            keyPassword = "cx123456"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        // 属性文件里的 Windows 路径由 Gradle 正常解析，这里关掉 lint 的转义误报
        disable += "PropertyEscape"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
    implementation("com.google.android.material:material:1.12.0")

    // JetBrains Compose（coui 依赖族）
    implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
    implementation("org.jetbrains.compose.ui:ui:1.11.1")
    implementation("org.jetbrains.compose.ui:ui-graphics:1.11.1")
    implementation("org.jetbrains.compose.ui:ui-text:1.11.1")

    // coui（Apache-2.0，ColorOS 17 风格组件库）
    implementation("io.github.suqi8.coui.kmp:coui-ui-android:2.0.0")
    implementation("io.github.suqi8.coui.kmp:coui-preference-android:2.0.0")
    implementation("io.github.suqi8.coui.kmp:coui-icons-android:2.0.0")
    implementation("io.github.suqi8.coui.kmp:coui-blur-android:2.0.0")
}

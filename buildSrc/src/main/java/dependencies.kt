object Configs {

    const val applicationId     = "org.albaspazio.k4b"
    const val compileSdkVersion = 28
    const val minSdkVersion     = 24
    const val targetSdkVersion  = 26
    const val versionCode       = 1
    const val versionName       = "1.0.0"
}

object Versions {

    const val kotlin = "1.3.72"
    const val ktx = "1.4.0-alpha01"
    const val gradlePlugin = "4.0.0"

    const val navVersion = "2.2.2"
    const val navSafeArgsGradlePlugin = "1.0.0"
    const val moshi = "1.9.2"
}

object Dependencies {

    object AndroidX {
        const val navFragment   = "androidx.navigation:navigation-fragment-ktx:${Versions.navVersion}"
        const val navUi         = "androidx.navigation:navigation-ui-ktx:${Versions.navVersion}"
        const val ktxCore       = "androidx.core:core-ktx:${Versions.ktx}"
    }

    object Kotlin {
        const val stdLib = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}"

    }

    object Moshi {
        const val moshi = "com.squareup.moshi:moshi:${Versions.moshi}"
        const val moshiKt = "com.squareup.moshi:moshi-kotlin:${Versions.moshi}"

    }
}

object ClassPaths {

    const val gradlePlugin = "com.android.tools.build:gradle:${Versions.gradlePlugin}"
    const val kotlinPlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"
    const val navSafeArgsGradlePlugin = "android.arch.navigation:navigation-safe-args-gradle-plugin:${Versions.navSafeArgsGradlePlugin}"
}


object Plugins {

    const val androidApplication    = "com.android.application"
    const val androidLibrary        = "com.android.library"
    const val kotlinAndroid         = "android"
    const val kotlinExtensions      = "android.extensions"
}


object ProGuards {

    val androidDefault = "proguard-rules.pro"
    val proguardTxt = "proguard-android.txt"
}
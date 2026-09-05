import java.util.Properties

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.nxy.hasstools"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.nxy.hasstools"
        minSdk = 31
        // 鸿蒙 4.x 安卓兼容层基于 Android 12(API 32)，实测 targetSdk ≥ 33 会被拒绝安装(解析包错误)
        targetSdk = 31
        versionCode = 9
        versionName = "1.0.8"

        val ciVersionCode = project.findProperty("versionCode") as String?
        val ciVersionName = project.findProperty("versionName") as String?

        if (ciVersionCode != null) {
            versionCode = ciVersionCode.toInt()
        }
        if (ciVersionName != null) {
            versionName = ciVersionName
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AMAP_API_KEY", """"${localProps.getProperty("AMAP_API_KEY", "")}"""")
    }

    signingConfigs {
        create("release") {
            // CI 环境：使用环境变量（Actions 里设置的）
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            val keyPassword = System.getenv("ANDROID_KEY_ALIAS_PASSWORD")

            if (!keystorePath.isNullOrBlank()) {
                storeFile = rootProject.file(keystorePath)
            }
            storePassword = keystorePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword

            // 鸿蒙要求 v3 签名，显式开启最稳妥
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    testOptions {
        unitTests.all {
            it.jvmArgs("-XX:+EnableDynamicAgentLoading")
        }
    }
    // 不上架 Google Play（走 GitHub Release），关闭其 targetSdk≥33 的强制 lint 检查；
    // 鸿蒙 4.x 兼容层要求 targetSdk≤32，二者冲突，此处以鸿蒙安装为准。
    lint {
        disable += "ExpiredTargetSdkVersion"
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
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
    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.okhttp)
    implementation(libs.reorderable)
    implementation(libs.amapLocation)
    // 完整版 BouncyCastle：系统 stripped 版 BC 读不了新版 OpenSSL 生成的 PKCS12
    implementation(libs.bouncycastle)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(kotlin("test"))
}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 서명 정보는 android/keystore.properties (git 제외) 에서 읽는다. 없으면 디버그 서명으로 release 를 만든다.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "kr.koasis.travelyunnan"
    compileSdk = 36

    defaultConfig {
        applicationId = "kr.koasis.travelyunnan"
        minSdk = 26
        targetSdk = 35
        versionCode = 39
        versionName = "1.6.6"
        // 영수증 인식(ML Kit) 네이티브 라이브러리가 CPU 종류마다 약 11MB 라서 실제 휴대폰용(ARM)만 넣는다(에뮬레이터 x86 제외)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // .so 를 압축해 내려받는 APK 크기를 줄인다(설치 때 풀림)
    packaging { jniLibs { useLegacyPackaging = true } }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreProps.isNotEmpty()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("roborazzi.test.record", "true")
            it.maxHeapSize = "3g"
        }
    }
    // 리포지토리 루트의 data/json, assets/* 를 빌드 때 앱 assets 로 동기화한다(S2-5).
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/tripAssets"))
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val syncTripAssets by tasks.registering(Sync::class) {
    val repo = rootProject.projectDir.parentFile
    into(layout.buildDirectory.dir("generated/tripAssets"))
    from(File(repo, "data/json")) { into("data"); include("*.json") }
    from(File(repo, "assets/maps")) { into("images/maps") }
    from(File(repo, "assets/foods")) { into("images/foods") }
    from(File(repo, "assets/sights")) { into("images/sights") }
    from(File(repo, "assets/eats")) { into("images/eats") }
    from(File(repo, "assets/audio")) { into("audio"); exclude("**/.gitkeep", "**/*.sha") }
}
tasks.named("preBuild") { dependsOn(syncTripAssets) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.biometric)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.mlkit.text.chinese)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}

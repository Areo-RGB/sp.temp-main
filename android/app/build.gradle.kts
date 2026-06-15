plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val ciVersionCode = providers.gradleProperty("ciVersionCode")
    .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER"))
    .orElse("1")
    .get()
    .toIntOrNull()
    ?: 1

val ciVersionName = providers.gradleProperty("ciVersionName")
    .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER").map { "debug-$it" })
    .orElse("1.0")
    .get()

val updateRepo = providers.gradleProperty("updateRepo")
    .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
    .orElse("Areo-RGB/sp.temp-main")
    .get()

android {
    namespace = "com.trapwire.racing"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trapwire.racing"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName

        buildConfigField("String", "UPDATE_REPO", updateRepo.asBuildConfigString())
        buildConfigField("String", "UPDATE_RELEASE_TAG", "debug-latest".asBuildConfigString())
        buildConfigField("String", "UPDATE_METADATA_ASSET", "trapwire-debug.json".asBuildConfigString())
        buildConfigField("String", "UPDATE_APK_ASSET", "trapwire-debug.apk".asBuildConfigString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-common")
    implementation("com.google.firebase:firebase-database")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}

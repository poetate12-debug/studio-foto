plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "id.reang.studiofoto.nativeapp"
    compileSdk = 35
    defaultConfig {
        applicationId = "id.reang.studiofoto.nativeapp"
        minSdk = 29
        targetSdk = 35
        versionCode = 11
        versionName = "1.4.0-native-presets-review"
    }
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("REANG_KEYSTORE_PATH")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("REANG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("REANG_KEY_ALIAS")
                keyPassword = System.getenv("REANG_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true }
    sourceSets.getByName("main").java.srcDir(layout.buildDirectory.dir("generated/source/lightcraft/kotlin"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // UniFFI's generated Kotlin bindings call the bundled LightCraft Rust engine through JNA.
    implementation("net.java.dev.jna:jna:5.19.1@aar")
    kapt("androidx.room:room-compiler:2.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
kapt { correctErrorTypes = true }

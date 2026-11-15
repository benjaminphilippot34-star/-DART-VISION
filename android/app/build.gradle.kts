plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStoreFile = System.getenv("DARTVISION_KEYSTORE_PATH")

android {
    namespace = "com.dartvision.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.dartvision.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.9.2"
    }
    signingConfigs {
        create("release") {
            if (!releaseStoreFile.isNullOrBlank()) {
                storeFile = file(releaseStoreFile)
                storePassword = System.getenv("DARTVISION_STORE_PASSWORD")
                keyAlias = System.getenv("DARTVISION_KEY_ALIAS")
                keyPassword = System.getenv("DARTVISION_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
kotlin {
    jvmToolchain(17)
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    testImplementation("junit:junit:4.13.2")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.maejung"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.maejung"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // 차량에 GMS가 없어 FCM 대신 WebSocket으로 요청을 받는다
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

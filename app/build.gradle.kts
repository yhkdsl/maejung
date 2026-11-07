import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// release 서버 주소는 local.properties 또는 -P의 maejung.serverUrl에서 읽는다(Railway 배포 후 사용자에게 받는다)
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val releaseServerUrl = (findProperty("maejung.serverUrl") as String?) ?: localProperties.getProperty("maejung.serverUrl")

android {
    namespace = "com.maejung"
    // 최신 AndroidX(Compose 1.8 등)가 compileSdk 35 이상을 요구한다. 실행 대상(에뮬레이터 API 34)은 그대로
    compileSdk = 35

    defaultConfig {
        // 서버 없이 흐름만 볼 때: ./gradlew :app:assembleDebug -Pmaejung.fakeServer=true
        buildConfigField("boolean", "FAKE_SERVER", (findProperty("maejung.fakeServer") == "true").toString())
        applicationId = "com.maejung"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // 에뮬레이터에서 호스트 PC의 로컬 서버
            buildConfigField("String", "SERVER_URL", "\"http://10.0.2.2:8080\"")
        }
        release {
            buildConfigField("String", "SERVER_URL", "\"${releaseServerUrl.orEmpty()}\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// 서버 주소 없이 release를 만들면 실행해도 서버에 못 붙으니 빌드를 막는다
gradle.taskGraph.whenReady {
    if (allTasks.any { it.project == project && it.name.contains("Release") } && releaseServerUrl.isNullOrBlank()) {
        throw GradleException("release 빌드에는 maejung.serverUrl(local.properties 또는 -P)이 필요합니다")
    }
}

dependencies {
    implementation(project(":protocol"))

    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    // 차량에 GMS가 없어 FCM 대신 WebSocket으로 요청을 받는다
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")

    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

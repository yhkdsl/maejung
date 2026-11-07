import java.util.Properties
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val releaseServerUrl = (findProperty("maejung.serverUrl") as String?)
    ?: localProperties.getProperty("maejung.serverUrl")

android {
    namespace = "com.maejung.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maejung.phone"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
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

gradle.taskGraph.whenReady {
    if (allTasks.any { it.project == project && it.name.contains("Release") }) {
        if (releaseServerUrl.isNullOrBlank()) {
            throw GradleException("release 빌드에는 maejung.serverUrl(local.properties 또는 -P)이 필요합니다")
        }
        val url = runCatching { URI(releaseServerUrl) }.getOrNull()
        if (url == null || url.scheme != "https" || url.host.isNullOrBlank() ||
            url.rawUserInfo != null || url.rawQuery != null || url.rawFragment != null) {
            throw GradleException("release maejung.serverUrl은 사용자 정보·쿼리·fragment 없는 HTTPS 서버 주소여야 합니다")
        }
    }
}

dependencies {
    implementation(project(":protocol"))
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
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

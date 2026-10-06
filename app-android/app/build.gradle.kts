plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "id.melvern.hermesmobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.melvern.hermesmobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 27
        versionName = "26.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    testOptions {
        // M9: TranscriptCache pakai android.util.Log — no-op di unit test
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // M15: adaptive layout — WindowSizeClass (Compact/Medium/Expanded) di HermesApp.
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-core")
    // M8: Icons.Rounded.* lengkap (satu keluarga) — pengganti emoji/unicode ikon
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // M9 (item 3): video player utk jawaban agent yang kirim file video
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("com.android.tools:desugar_jdk_libs:2.1.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

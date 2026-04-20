plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace   = "com.cubicauto"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.cubicauto"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"

        // REPLACE with your Spotify Developer Dashboard client ID
        buildConfigField("String", "SPOTIFY_CLIENT_ID",    "\"YOUR_SPOTIFY_CLIENT_ID\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"cubicauto://callback\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose     = true
        buildConfig = true
    }
    // Spotify SDK .aar goes in app/libs/ when you have it.
    // Uncomment the line below once you've dropped in spotify-app-remote-release-0.8.0.aar
    // dependencies {
    //     implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    // }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.canvas)
    implementation(libs.androidx.media)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.accompanist.systemuicontroller)
    debugImplementation(libs.compose.ui.tooling)
}

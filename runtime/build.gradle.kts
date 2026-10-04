plugins {
    id("com.android.library")
}

android {
    namespace = "com.ivanchan.launcher.combined.transitions"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

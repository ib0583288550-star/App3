plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.ybapps.offlineapkbackup"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ybapps.offlineapkbackup"
        minSdk = 21
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }
}
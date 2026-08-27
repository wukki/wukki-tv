import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("androidApp/keystore.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use(::load)
}

android {
    namespace = "hu.wukki.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "hu.wukki.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures { compose = true }

    if (releaseSigningProperties.isNotEmpty()) {
        signingConfigs.create("wukkiRelease") {
            storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
            storePassword = releaseSigningProperties.getProperty("storePassword")
            keyAlias = releaseSigningProperties.getProperty("keyAlias")
            keyPassword = releaseSigningProperties.getProperty("keyPassword")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("wukkiRelease")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.compose.runtime:runtime:1.12.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
}

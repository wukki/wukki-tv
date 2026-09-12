import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    id("wukki.quality")
    id("wukki.dependency-locking")
}

val releaseSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("androidApp/keystore.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use(::load)
}
val wukkiVersion = rootProject.extra["wukkiDisplayVersion"].toString()
val wukkiVersionCode = rootProject.extra["wukkiAndroidVersionCode"] as Int

android {
    namespace = "hu.wukki.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "hu.wukki.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = wukkiVersionCode
        versionName = wukkiVersion
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
    implementation(libs.bundles.compose.common)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.android.activity.compose)
    implementation(libs.android.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.android.datastore.preferences)
    implementation(libs.android.work.runtime)
    implementation(libs.bundles.android.media3)
    testImplementation(kotlin("test-junit"))
}

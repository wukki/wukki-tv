plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("multiplatform") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.12.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.android.application") version "9.1.0" apply false
    id("com.android.kotlin.multiplatform.library") version "9.1.0" apply false
}

val wukkiVersionInfo = WukkiVersioning.resolve(project)

extra["wukkiDisplayVersion"] = wukkiVersionInfo.displayVersion
extra["wukkiBuildId"] = wukkiVersionInfo.buildId
extra["wukkiPackageVersion"] = wukkiVersionInfo.packageVersion
extra["wukkiAndroidVersionCode"] = wukkiVersionInfo.androidVersionCode
extra["wukkiBuildDate"] = wukkiVersionInfo.buildDate.toString()
extra["wukkiRunNumber"] = wukkiVersionInfo.runNumber

tasks.register("printWukkiVersion") {
    group = "help"
    description = "Prints the user-visible and platform-internal Wukki TV version metadata."
    doLast {
        println("displayVersion=${wukkiVersionInfo.displayVersion}")
        println("buildId=${wukkiVersionInfo.buildId}")
        println("packageVersion=${wukkiVersionInfo.packageVersion}")
        println("androidVersionCode=${wukkiVersionInfo.androidVersionCode}")
        println("buildDate=${wukkiVersionInfo.buildDate}")
        println("runNumber=${wukkiVersionInfo.runNumber}")
    }
}

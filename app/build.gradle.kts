import org.gradle.api.tasks.Sync

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val generatedAppResources = layout.buildDirectory.dir("generated/wukkiAppResources")
val vlcRuntimePath = providers.environmentVariable("WUKKI_VLC_RUNTIME")
val prepareVlcRuntime by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Places an externally supplied VLC runtime and third-party notices into the app resources."
    from(rootProject.layout.projectDirectory.dir("LICENSES")) {
        into("common/licenses")
    }
    inputs.property("vlcRuntimePath", vlcRuntimePath.orElse(""))
    vlcRuntimePath.orNull?.let { runtimePath ->
        from(file(runtimePath)) { into("common/runtime/vlc") }
    }
    into(generatedAppResources)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
                implementation("io.coil-kt.coil3:coil-compose:3.0.4")
                implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")
                implementation("io.coil-kt.coil3:coil-svg:3.0.4")
                // Coil's Ktor integration needs a JVM HTTP engine for remote tvg-logo URLs.
                implementation("io.ktor:ktor-client-java:3.0.1")
                implementation("uk.co.caprica:vlcj:4.8.3")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "hu.wukki.tv.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "Wukki TV"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(generatedAppResources)
        }
    }
}

tasks.matching { it.name == "prepareAppResources" || it.name.startsWith("package") }.configureEach {
    dependsOn(prepareVlcRuntime)
}

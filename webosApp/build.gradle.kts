import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("wukki.quality")
    id("wukki.dependency-locking")
}

plugins.withType<NodeJsPlugin>().configureEach {
    extensions.configure<NodeJsEnvSpec> {
        download.set(false)
        command.set("node")
    }
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "wukki-tv-webos.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            resources.srcDir("../packaging/icons")
            resources.exclude("*.icns", "*.ico")
            dependencies {
                implementation(project(":core"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

val webOsDistribution = layout.buildDirectory.dir("dist/js/productionExecutable")
val webOsPackageOutput = layout.buildDirectory.dir("outputs/webos")

tasks.register<Exec>("packageWebOs") {
    group = "distribution"
    description = "Builds and packages the webOS application as an IPK with the LG webOS CLI."
    dependsOn("jsBrowserDistribution")
    inputs.dir(webOsDistribution)
    outputs.dir(webOsPackageOutput)

    doFirst {
        webOsPackageOutput.get().asFile.mkdirs()
    }
    commandLine(
        "ares-package",
        "--no-minify",
        "--outdir",
        webOsPackageOutput.get().asFile.absolutePath,
        webOsDistribution.get().asFile.absolutePath,
    )
}

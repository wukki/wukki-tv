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
        compilerOptions {
            target.set("es5")
        }
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
                implementation(devNpm("@babel/core", "7.28.5"))
                implementation(devNpm("@babel/preset-env", "7.28.5"))
                implementation(devNpm("babel-loader", "10.0.0"))
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
val verifyWebOs5Bundle by tasks.registering {
    group = "verification"
    description = "Rejects JavaScript syntax unsupported by the Chromium 68 engine in webOS 5."
    dependsOn("jsBrowserDistribution")

    val bundle = webOsDistribution.map { it.file("wukki-tv-webos.js") }
    inputs.file(bundle)

    doLast {
        val source = bundle.get().asFile.readText()
        val unsupported = listOf("??" to "nullish coalescing", "?." to "optional chaining")
        val detected = unsupported.filter { (token, _) -> token in source }.map { (_, name) -> name }
        check(detected.isEmpty()) {
            "The webOS bundle contains syntax unsupported by Chromium 68: ${detected.joinToString()}"
        }
    }
}

tasks.register<Exec>("packageWebOs") {
    group = "distribution"
    description = "Builds and packages the webOS application as an IPK with the LG webOS CLI."
    dependsOn(verifyWebOs5Bundle)
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

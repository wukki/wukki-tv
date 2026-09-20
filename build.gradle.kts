import dev.detekt.gradle.extensions.DetektExtension
import org.cyclonedx.gradle.BaseCyclonedxTask
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

plugins.withType<NodeJsPlugin>().configureEach {
    extensions.configure<NodeJsEnvSpec> {
        download.set(false)
        command.set("node")
    }
}

val wukkiVersionInfo = WukkiVersioning.resolve(project)

allprojects {
    group = "hu.wukki.tv"
    version = wukkiVersionInfo.displayVersion

    tasks.withType<CyclonedxDirectTask>().configureEach {
        includeConfigs.set(
            when (project.path) {
                ":androidApp" -> listOf("releaseRuntimeClasspath")
                ":desktopApp" -> listOf("runtimeClasspath")
                ":shared" -> listOf("androidRuntimeClasspath", "desktopRuntimeClasspath")
                ":core" -> listOf("androidRuntimeClasspath", "desktopRuntimeClasspath", "jsRuntimeClasspath")
                ":webosApp" -> listOf("jsRuntimeClasspath")
                else -> listOf("(?!)")
            },
        )
    }
}

subprojects {
    pluginManager.withPlugin("dev.detekt") {
        extensions.configure<DetektExtension> {
            toolVersion.set(libs.versions.detekt)
            source.setFrom(fileTree("src") { include("**/*.kt") })
            config.setFrom(rootProject.files("config/detekt.yml"))
            baseline.set(file("config/detekt-baseline.xml"))
            parallel.set(true)
            basePath.set(rootProject.projectDir)
        }
    }
    pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<KtlintExtension> {
            version.set(libs.versions.ktlint.cli)
            baseline.set(file("config/ktlint-baseline.xml"))
            outputToConsole.set(true)
            filter { exclude("**/generated/**", "**/build/**") }
        }
    }
}

tasks.withType<BaseCyclonedxTask>().configureEach {
    componentGroup.set("hu.wukki.tv")
    componentName.set("wukki-tv")
    componentVersion.set(wukkiVersionInfo.displayVersion)
    includeLicenseText.set(false)
}

tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    enabled = false
}

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

val verifyBuildLogic by tasks.registering(GradleBuild::class) {
    group = "verification"
    description = "Runs the versioning tests in the separate buildSrc build."
    dir = file("buildSrc")
    buildName = "wukki-build-logic-verification"
    tasks = listOf("test")
}

val verifySupplyChainConfiguration by tasks.registering {
    group = "verification"
    description = "Checks dependency locks, verification metadata and immutable GitHub Action references."
    mustRunAfter("kotlinStorePackageLock")

    val workflowFiles = fileTree(".github/workflows") { include("*.yml", "*.yaml") }
    inputs.files(workflowFiles)
    inputs.files(
        "core/gradle.lockfile",
        "shared/gradle.lockfile",
        "androidApp/gradle.lockfile",
        "webosApp/gradle.lockfile",
        "kotlin-js-store/package-lock.json",
        "gradle/verification-metadata.xml",
    )

    doLast {
        val requiredFiles =
            listOf(
                file("shared/gradle.lockfile"),
                file("androidApp/gradle.lockfile"),
                file("core/gradle.lockfile"),
                file("webosApp/gradle.lockfile"),
                file("kotlin-js-store/package-lock.json"),
                file("gradle/verification-metadata.xml"),
            )
        val missingFiles = requiredFiles.filterNot { it.isFile }
        check(missingFiles.isEmpty()) {
            "Missing supply-chain files: ${missingFiles.joinToString { it.relativeTo(rootDir).path }}"
        }

        val actionReference = Regex("""^\s*-?\s*uses:\s*([^\s#]+)""")
        val immutableAction = Regex("""^[^@]+@[0-9a-f]{40}$""")
        val mutableReferences =
            workflowFiles.files.flatMap { workflow ->
                workflow.readLines().mapIndexedNotNull { index, line ->
                    val reference = actionReference.find(line)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
                    if (reference.startsWith("./") || immutableAction.matches(reference)) {
                        null
                    } else {
                        "${workflow.relativeTo(rootDir).path}:${index + 1}: $reference"
                    }
                }
            }
        check(mutableReferences.isEmpty()) {
            "GitHub Actions must use immutable 40-character commit SHAs:\n${mutableReferences.joinToString("\n")}"
        }

        val actionlintImage =
            "rhysd/actionlint:1.7.12@sha256:b1934ee5f1c509618f2508e6eb47ee0d3520686341fec936f3b79331f9315667"
        val workflowsUsingActionlint = workflowFiles.files.filter { "rhysd/actionlint:" in it.readText() }
        check(workflowsUsingActionlint.all { actionlintImage in it.readText() }) {
            "The actionlint container must use its reviewed multi-platform image digest."
        }
    }
}

tasks.register("verifyAll") {
    group = "verification"
    description = "Runs build-logic tests, application quality checks, desktop/Android tests and the Android debug build."
    dependsOn(verifyBuildLogic)
    dependsOn(verifySupplyChainConfiguration)
    dependsOn(
        ":shared:detekt",
        ":shared:ktlintCheck",
        ":shared:desktopTest",
        ":shared:testAndroidHostTest",
        ":shared:checkLocalizationBundles",
        ":core:desktopTest",
        ":core:jsBrowserTest",
        ":core:detekt",
        ":core:ktlintCheck",
        ":webosApp:verifyWebOs5Bundle",
        ":webosApp:detekt",
        ":webosApp:ktlintCheck",
        ":desktopApp:detekt",
        ":desktopApp:ktlintCheck",
        ":desktopApp:test",
        ":desktopApp:compileKotlin",
        ":androidApp:detekt",
        ":androidApp:ktlintCheck",
        ":androidApp:testDebugUnitTest",
        ":androidApp:assembleDebug",
    )
}

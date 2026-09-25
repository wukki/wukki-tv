import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    id("wukki.quality")
}

kotlin {
    jvmToolchain(21)
}

val generatedAppResources = layout.buildDirectory.dir("generated/wukkiAppResources")
val vlcRuntimePath = providers.environmentVariable("WUKKI_VLC_RUNTIME")
val wukkiPackageVersion = rootProject.extra["wukkiPackageVersion"].toString()
val macSigningIdentity = providers.gradleProperty("macSigningIdentity")
val macSigningKeychain = providers.gradleProperty("macSigningKeychain")
val macSigningPrefix = providers.gradleProperty("macSigningPrefix")
val macNotarizationAppleId = providers.gradleProperty("macNotarizationAppleId")
val macNotarizationPassword = providers.gradleProperty("macNotarizationPassword")
val macNotarizationTeamId = providers.gradleProperty("macNotarizationTeamId")
val macDmgVolumeIcon = rootProject.layout.projectDirectory.file("packaging/icons/wukki-tv.icns")
val applyMacDmgVolumeIcon = rootProject.layout.projectDirectory.file("packaging/macos/apply-dmg-volume-icon.sh")

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.core)
    implementation(libs.ktor.client.java)
    implementation(libs.vlcj)
    implementation(libs.jna.core)
    implementation(libs.jna.platform)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    options.release.set(21)
}

val java21Launcher =
    extensions.getByType<JavaToolchainService>().launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }

val prepareVlcRuntime by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Places an externally supplied VLC runtime and third-party notices into app resources."
    from(rootProject.layout.projectDirectory.dir("LICENSES")) { into("common/licenses") }
    inputs.property("vlcRuntimePath", vlcRuntimePath.orElse(""))
    vlcRuntimePath.orNull?.let { runtimePath ->
        from(file(runtimePath)) {
            exclude("plugins/plugins.dat")
            into("common/runtime/vlc")
        }
    }
    into(generatedAppResources)
}

val patchMacVlcRuntime by tasks.registering {
    group = "distribution"
    description = "Adds loader-relative VLC library paths to the packaged macOS runtime."
    dependsOn(prepareVlcRuntime)
    onlyIf { System.getProperty("os.name").startsWith("Mac", ignoreCase = true) && vlcRuntimePath.isPresent }
    inputs.dir(generatedAppResources)
    doLast {
        val runtime = generatedAppResources.get().dir("common/runtime/vlc").asFile

        fun runCommand(vararg arguments: String): String {
            val process = ProcessBuilder(*arguments).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            check(process.waitFor() == 0) { "Command failed: ${arguments.joinToString(" ")}\\n$output" }
            return output
        }

        fun addRpath(
            binary: File,
            rpath: String,
        ) {
            if (!runCommand("otool", "-l", binary.absolutePath).contains("path $rpath (")) {
                runCommand("install_name_tool", "-add_rpath", rpath, binary.absolutePath)
            }
        }
        runtime
            .resolve("lib")
            .listFiles { file -> file.extension == "dylib" }
            ?.forEach { addRpath(it, "@loader_path") }
    }
}

compose.desktop {
    application {
        mainClass = "hu.wukki.tv.MainKt"
        nativeDistributions {
            modules("jdk.unsupported")
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "Wukki TV"
            packageVersion = wukkiPackageVersion
            macOS {
                iconFile.set(rootProject.file("packaging/icons/wukki-tv.icns"))
                packageBuildVersion = wukkiPackageVersion
                if (macSigningIdentity.isPresent) {
                    signing {
                        sign.set(true)
                        identity.set(macSigningIdentity)
                        keychain.set(macSigningKeychain)
                        prefix.set(macSigningPrefix)
                    }
                }
                if (macNotarizationAppleId.isPresent) {
                    notarization {
                        appleID.set(macNotarizationAppleId)
                        password.set(macNotarizationPassword)
                        teamID.set(macNotarizationTeamId)
                    }
                }
            }
            windows {
                iconFile.set(rootProject.file("packaging/icons/wukki-tv.ico"))
                msiPackageVersion = wukkiPackageVersion
            }
            linux {
                iconFile.set(rootProject.file("packaging/icons/wukki-tv.png"))
                debPackageVersion = wukkiPackageVersion
            }
            appResourcesRootDir.set(generatedAppResources)
        }
    }
}

afterEvaluate {
    tasks.named<JavaExec>("run") {
        setExecutable(
            java21Launcher
                .get()
                .executablePath.asFile.absolutePath,
        )
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

tasks.matching { it.name == "prepareAppResources" || it.name.startsWith("package") }.configureEach {
    dependsOn(patchMacVlcRuntime)
}

tasks.withType<AbstractJPackageTask>().configureEach {
    if (targetFormat == TargetFormat.Dmg) {
        inputs.file(macDmgVolumeIcon)
        inputs.file(applyMacDmgVolumeIcon)
        doLast {
            check(System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
                "DMG volume icons can only be applied on macOS"
            }
            val dmgFiles =
                destinationDir
                    .get()
                    .asFile
                    .listFiles { file -> file.isFile && file.extension.equals("dmg", ignoreCase = true) }
                    .orEmpty()
            check(dmgFiles.size == 1) {
                "Expected exactly one DMG in ${destinationDir.get().asFile}, found ${dmgFiles.size}"
            }

            val command =
                mutableListOf(
                    applyMacDmgVolumeIcon.asFile.absolutePath,
                    dmgFiles.single().absolutePath,
                    macDmgVolumeIcon.asFile.absolutePath,
                )
            macSigningIdentity.orNull?.takeIf { it.isNotBlank() }?.let { identity ->
                command += listOf("--signing-identity", identity)
                macSigningKeychain.orNull?.takeIf { it.isNotBlank() }?.let { keychain ->
                    command += listOf("--keychain", keychain)
                }
            }

            val process = ProcessBuilder(command).inheritIO().start()
            check(process.waitFor() == 0) { "Failed to apply the Wukki TV DMG volume icon" }
        }
    }
}

// WOS-14 reference harness is compiled only into tests, never into the shipped application.
kotlin.sourceSets.named("test") { kotlin.srcDir(rootProject.file("tools/parity/kotlin")) }
tasks.withType<Test>().configureEach {
    systemProperty("user.timezone", "Europe/Budapest")
    systemProperty("parity.width", providers.gradleProperty("parityWidth").getOrElse("1920"))
    systemProperty("parity.height", providers.gradleProperty("parityHeight").getOrElse("1080"))
    systemProperty("parity.traces", rootProject.file("docs/webos-parity/v1/input-traces.json").absolutePath)
    systemProperty("parity.output", providers.gradleProperty("parityOutput").getOrElse(rootProject.file("docs/webos-parity/v1/screenshots/desktop").absolutePath))
}

tasks.named<Test>("test") { filter { excludeTestsMatching("*ParityCaptureTest") } }
tasks.register<Test>("captureParityReferences") {
    group = "verification"
    description = "Explicitly regenerates WOS-14 desktop reference images; review changes before committing."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("*ParityCaptureTest") }
    outputs.upToDateWhen { false }
}

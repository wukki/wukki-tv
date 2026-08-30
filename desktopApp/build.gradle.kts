import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(21)
}

val generatedAppResources = layout.buildDirectory.dir("generated/wukkiAppResources")
val vlcRuntimePath = providers.environmentVariable("WUKKI_VLC_RUNTIME")
val wukkiVersion = providers.gradleProperty("wukkiVersion")
    .orElse(providers.environmentVariable("GITHUB_REF_NAME").map { it.removePrefix("v") })
    .orElse("1.0.0")

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("io.ktor:ktor-client-java:3.0.1")
    implementation("uk.co.caprica:vlcj:4.8.3")
    implementation("net.java.dev.jna:jna-jpms:5.14.0")
    implementation("net.java.dev.jna:jna-platform-jpms:5.14.0")
    testImplementation(kotlin("test"))
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    options.release.set(21)
}

val java21Launcher = extensions.getByType<JavaToolchainService>().launcherFor {
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
        fun addRpath(binary: File, rpath: String) {
            if (!runCommand("otool", "-l", binary.absolutePath).contains("path $rpath (")) {
                runCommand("install_name_tool", "-add_rpath", rpath, binary.absolutePath)
            }
        }
        runtime.resolve("lib").listFiles { file -> file.extension == "dylib" }
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
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "Wukki TV"
            packageVersion = wukkiVersion.get()
            macOS { iconFile.set(rootProject.file("packaging/icons/wukki-tv.icns")) }
            windows { iconFile.set(rootProject.file("packaging/icons/wukki-tv.ico")) }
            linux { iconFile.set(rootProject.file("packaging/icons/wukki-tv.png")) }
            appResourcesRootDir.set(generatedAppResources)
        }
    }
}

afterEvaluate {
    tasks.named<JavaExec>("run") {
        setExecutable(java21Launcher.get().executablePath.asFile.absolutePath)
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

tasks.matching { it.name == "prepareAppResources" || it.name.startsWith("package") }.configureEach {
    dependsOn(patchMacVlcRuntime)
}

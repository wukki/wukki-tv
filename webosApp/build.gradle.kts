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
            resources.srcDir("../shared/src/commonMain/resources")
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
val webOsServiceDirectory = rootProject.layout.projectDirectory.dir("webosService")
val webOsPreviewServer = layout.projectDirectory.file("preview-server.js")
val verifyWebOsAppShell by tasks.registering {
    group = "verification"
    description = "Checks the WOS-15–23 shell, navigation, playback, settings, persistence, lifecycle and guide contracts."
    val markup = layout.projectDirectory.file("src/jsMain/resources/index.html")
    val styles = layout.projectDirectory.file("src/jsMain/resources/styles.css")
    val appInfo = layout.projectDirectory.file("src/jsMain/resources/appinfo.json")
    val embeddedResources = layout.projectDirectory.file("src/jsMain/resources/embedded-resources.js")
    val remoteAdapter = layout.projectDirectory.file("src/jsMain/kotlin/hu/wukki/tv/webos/WebOsRemoteController.kt")
    val liveTiming = layout.projectDirectory.file("src/jsMain/kotlin/hu/wukki/tv/webos/LiveLayerTiming.kt")
    val playbackSession = layout.projectDirectory.file("src/jsMain/kotlin/hu/wukki/tv/webos/WebOsPlaybackSession.kt")
    val mainSource = layout.projectDirectory.file("src/jsMain/kotlin/hu/wukki/tv/webos/Main.kt")
    val stateStore = layout.projectDirectory.file("src/jsMain/kotlin/hu/wukki/tv/webos/WebOsStateStore.kt")
    val epgData = layout.projectDirectory.file("src/jsMain/kotlin/hu/wukki/tv/webos/WebOsEpgData.kt")
    val guideState = layout.projectDirectory.file("src/jsMain/kotlin/hu/wukki/tv/webos/WebOsGuideState.kt")
    val guideView = layout.projectDirectory.file("src/jsMain/kotlin/hu/wukki/tv/webos/WebOsGuideView.kt")
    val xmlTvParser = rootProject.layout.projectDirectory.file("core/src/commonMain/kotlin/hu/wukki/tv/XmlTvProgrammeParser.kt")
    val serviceSource = webOsServiceDirectory.file("epg-service.js")
    val serviceInfo = webOsServiceDirectory.file("services.json")
    val serviceCertificateAuthority = webOsServiceDirectory.file("certificates/isrg-root-x1.pem")
    val sharedReducer = rootProject.layout.projectDirectory.file("core/src/commonMain/kotlin/hu/wukki/tv/ui/navigation/AppRemoteReducer.kt")
    inputs.files(markup, styles, appInfo, embeddedResources, remoteAdapter, liveTiming, playbackSession, mainSource, stateStore, epgData, guideState, guideView, xmlTvParser, serviceSource, serviceInfo, serviceCertificateAuthority, sharedReducer)

    doLast {
        val html = markup.asFile.readText()
        val css = styles.asFile.readText()
        val appInfoJson = appInfo.asFile.readText()
        val routes = Regex("""data-route="([^"]+)"""").findAll(html).map { it.groupValues[1] }.toList()
        val navigation = Regex("""class="nav-item"[^>]*data-section="([^"]+)"""").findAll(html).map { it.groupValues[1] }.toList()
        val expected = listOf("live", "guide", "channels", "settings")
        check(routes == expected) { "Unexpected webOS route order: $routes" }
        check(navigation == expected) { "Unexpected webOS navigation order: $navigation" }
        check(Regex("""id="player"""").findAll(html).count() == 1) { "The shell must own exactly one persistent video element." }
        val settingsMarkup = html.substringAfter("id=\"view-settings\"")
        check("id=\"diagnostic-url\"" in settingsMarkup && "id=\"source-url\"" in settingsMarkup) {
            "Diagnostics must live in Settings."
        }
        check("id=\"diagnostic-url\"" !in html.substringBefore("id=\"view-settings\"")) {
            "Diagnostics leaked into a primary content screen."
        }
        listOf("#07101a", "#101d2b", "#8b5cf6", "#a277ff", "#2d214d").forEach { token ->
            check(token in css.lowercase()) { "Missing Wukki theme token $token" }
        }
        check("width: 1920px" !in css && "height: 1080px" !in css) {
            "The app shell must fit the viewport instead of clipping to one fixed resolution."
        }
        check("\"disableBackHistoryAPI\": true" in appInfoJson) { "The shared Back state machine must receive the LG Back key." }
        val embeddedSource = embeddedResources.asFile.readText()
        check("embedded-resources.js" in html && "WUKKI_EMBEDDED_RESOURCES" in embeddedSource) {
            "Local resources must be embedded instead of loaded through file-origin XHR."
        }
        listOf("i18n/messages_hu.properties", "i18n/messages_en.properties", "legal/privacy_hu.txt", "legal/privacy_en.txt").forEach { path ->
            check("\"$path\"" in embeddedSource) { "Missing embedded webOS resource $path." }
        }
        listOf("previous-channel", "channel-down", "channel-up", "quick-settings", "close-quick-settings").forEach { id ->
            check("id=\"$id\"" in html) { "Missing visible remote-control fallback #$id." }
        }
        check("AppRemoteState" in remoteAdapter.asFile.readText() && sharedReducer.asFile.exists()) {
            "The webOS adapter must use the shared KMP remote reducer."
        }
        listOf("channel-tabs", "open-channel-search", "channel-preview", "channel-empty-action").forEach { id ->
            check("id=\"$id\"" in html) { "Missing WOS-17 channel-browser control #$id." }
        }
        check(".channel-browser-layout > .channel-surface" in css && "flex: 0 0 calc(62% - 6px)" in css) {
            "The Channels screen must keep the shared 62/38 list and preview layout with legacy-compatible flexbox."
        }
        check("category-filter" !in html) { "The obsolete cyclic category button must not return." }
        check("\"version\": \"0.15.0\"" in appInfoJson) { "WOS-23 must package as webOS version 0.15.0." }
        listOf("playback-hud", "live-channel-number", "live-channel-logo", "live-programme-progress", "channel-number-input").forEach { id ->
            check("id=\"$id\"" in html) { "Missing WOS-18 live information element #$id." }
        }
        check("live-navigation-hidden" in css && "pointer-events: none" in css) {
            "Hidden live navigation must not retain pointer interaction."
        }
        val timingSource = liveTiming.asFile.readText()
        check("LiveLayer.NAVIGATION" in timingSource && "LiveLayer.INFORMATION_PANEL" in timingSource && "LiveLayer.DIALOG" in timingSource) {
            "Live navigation, information panel and dialog timing must remain independent."
        }
        listOf("playback-state-overlay", "cancel-reconnect", "playback-recovery", "retry-playback", "open-channels-after-error").forEach { id ->
            check("id=\"$id\"" in html) { "Missing WOS-19 playback recovery control #$id." }
        }
        val sessionSource = playbackSession.asFile.readText()
        check("generation" in sessionSource && "reconnectAttempts" in sessionSource && "accepts(token" in sessionSource) {
            "WOS-19 requires generation-safe, settings-driven playback recovery."
        }
        val lifecycleSource = mainSource.asFile.readText()
        val stateSource = stateStore.asFile.readText()
        check("visibilitychange" in lifecycleSource && "pauseForBackground" in lifecycleSource && "resumeAfterBackground" in lifecycleSource) {
            "WOS-22 requires explicit background and foreground lifecycle handling."
        }
        check("stateWritesBlocked" in lifecycleSource && "WEBOS_STATE_SCHEMA_VERSION = 2" in stateSource && "WEBOS_LEGACY_STATE_SCHEMA_VERSION = 1" in stateSource) {
            "WOS-22 requires guarded persistence and migration from the legacy state schema."
        }
        listOf("channel-preview-current", "channel-preview-next", "channel-preview-progress", "channel-preview-programme-image").forEach { id ->
            check("id=\"$id\"" in html) { "Missing WOS-20 programme-data element #$id." }
        }
        val epgSource = epgData.asFile.readText()
        val parserSource = xmlTvParser.asFile.readText()
        check("parseBatch" in epgSource && "WEBOS_EPG_STORAGE_KEY" in epgSource && "batchSize" in epgSource) {
            "WOS-20 requires bounded asynchronous EPG parsing and a separate cache."
        }
        check("MAX_DOCUMENT_BYTES" in parserSource && "unsafeDeclaration" in parserSource && "parseTimestamp" in parserSource) {
            "WOS-20 requires a bounded parser with disabled declarations and deterministic timezone handling."
        }
        check("webOSTV.js" in html && "fetchEpg" in serviceSource.asFile.readText() && "readChunk" in serviceInfo.asFile.readText()) {
            "WOS-20 must use the packaged JS service for CORS-restricted XMLTV sources."
        }
        val epgServiceSource = serviceSource.asFile.readText()
        val certificateAuthority = serviceCertificateAuthority.asFile.readText()
        check("isUnknownIssuer" in epgServiceSource && "BUNDLED_ISRG_ROOT_X1" in epgServiceSource) {
            "The webOS EPG service must retry obsolete trust stores with the bundled ISRG Root X1."
        }
        check("rejectUnauthorized" !in epgServiceSource) {
            "The EPG TLS workaround must never disable certificate validation."
        }
        check(certificateAuthority.startsWith("-----BEGIN CERTIFICATE-----") && certificateAuthority.contains("-----END CERTIFICATE-----")) {
            "The packaged ISRG Root X1 PEM is missing or malformed."
        }
        listOf("guide-actions", "guide-timeline", "guide-rows", "guide-programme-dialog").forEach { id ->
            check("id=\"$id\"" in html) { "Missing WOS-23 guide control #$id." }
        }
        check(".guide-row" in css && ".guide-programme" in css && ".guide-now-line" in css) {
            "WOS-23 requires channel rows, programme blocks and a current-time marker."
        }
        val guideStateSource = guideState.asFile.readText()
        val guideViewSource = guideView.asFile.readText()
        check("guideVisibleRows" in guideStateSource && "WEBOS_GUIDE_WINDOW_MILLIS" in guideStateSource) {
            "WOS-23 requires virtualized rows and a bounded time window."
        }
        check("GuideProgrammeDialogState" in guideViewSource && "handleGuideKey" in remoteAdapter.asFile.readText()) {
            "WOS-23 requires the shared programme dialog behavior and remote-control routing."
        }
        listOf("settings-detail", "settings-home", "quick-aspect-options", "legal-dialog").forEach { id ->
            check("id=\"$id\"" in html) { "Missing WOS-21 settings control #$id." }
        }

        fun cssRule(selector: String): String {
            val start = css.indexOf("$selector {")
            check(start >= 0) { "Missing CSS rule for $selector." }
            val end = css.indexOf('}', start)
            check(end >= 0) { "Unclosed CSS rule for $selector." }
            return css.substring(start, end + 1)
        }
        val legacyLayoutContracts =
            listOf(
                Triple(".top-navigation", "display: flex", "horizontal top navigation"),
                Triple(".brand", "flex: 0 0 20%", "20% brand column"),
                Triple(".nav-item", "flex: 0 0 calc(20% - 6px)", "20% navigation columns"),
                Triple(".channel-browser-layout", "display: flex", "two-panel Channels layout"),
                Triple(".settings-layout", "display: flex", "two-panel Settings layout"),
                Triple(".settings-categories", "flex: 0 0 430px", "fixed Settings category panel"),
            )
        legacyLayoutContracts.forEach { (selector, declaration, contract) ->
            check(declaration in cssRule(selector)) { "Missing WOS-21.1 legacy webOS layout contract: $contract." }
        }
    }
}

val verifyWebOsService by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks the syntax of the packaged WOS-20 XMLTV service."
    inputs.dir(webOsServiceDirectory)
    commandLine("node", "--check", webOsServiceDirectory.file("epg-service.js").asFile.absolutePath)
}

val verifyWebOsPreviewServer by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks the syntax of the local webOS preview and CORS proxy server."
    inputs.file(webOsPreviewServer)
    commandLine("node", "--check", webOsPreviewServer.asFile.absolutePath)
}

val verifyWebOs5Bundle by tasks.registering {
    group = "verification"
    description = "Rejects JavaScript syntax unsupported by the Chromium 68 engine in webOS 5."
    dependsOn("jsBrowserDistribution")

    val bundle = webOsDistribution.map { it.file("wukki-tv-webos.js") }
    inputs.file(bundle)

    doLast {
        val source = bundle.get().asFile.readText()
        val unsupported =
            listOf(
                Regex("(?<=[A-Za-z0-9_$)\\]])\\?\\?(?=[A-Za-z0-9_$('\\\"\\[])") to "nullish coalescing",
                Regex("(?<=[A-Za-z0-9_$)\\]])\\?\\.(?=[A-Za-z0-9_$\\[(])") to "optional chaining",
            )
        val detected = unsupported.filter { (pattern, _) -> pattern.containsMatchIn(source) }.map { (_, name) -> name }
        check(detected.isEmpty()) {
            "The webOS bundle contains syntax unsupported by Chromium 68: ${detected.joinToString()}"
        }
    }
}

tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyWebOsAppShell, verifyWebOsService, verifyWebOsPreviewServer) }

tasks.register<Exec>("previewWebOs") {
    group = "application"
    description = "Serves the webOS browser preview with a bounded EPG proxy on http://127.0.0.1:4173/."
    dependsOn("jsBrowserDistribution", verifyWebOsPreviewServer)
    commandLine("node", webOsPreviewServer.asFile.absolutePath)
}

tasks.register<Exec>("packageWebOs") {
    group = "distribution"
    description = "Builds and packages the webOS application as an IPK with the LG webOS CLI."
    dependsOn(verifyWebOs5Bundle, verifyWebOsService)
    inputs.dir(webOsDistribution)
    inputs.dir(webOsServiceDirectory)
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
        webOsServiceDirectory.asFile.absolutePath,
    )
}

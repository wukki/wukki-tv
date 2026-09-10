# Kotlin quality gates

`./gradlew verifyAll` is the shared local, CI, and release verification entry point. It runs
the buildSrc versioning tests, detekt and ktlint for all three modules, shared desktop and
Android host tests, desktop and Android unit tests, the localization-bundle check, desktop
compilation, and the Android debug APK build. Java 21 and a configured Android SDK are required.
Workflow linting (`actionlint`) remains a separate CI step; native installer packaging runs in
the release jobs.

Detekt enables the complexity rules and `WildcardImport`. `LongMethod` allows at most 230 lines;
`WukkiApp` is intentionally absent from the baseline so growing it beyond that limit fails the build.

The module-specific detekt and ktlint baselines contain findings that existed when the gates were
introduced. Do not regenerate a baseline for routine changes. Update it only when an existing
finding is fixed or a rule change is reviewed separately.

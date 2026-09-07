# Kotlin quality gates

`./gradlew check` runs detekt, ktlint, tests, and the shared localization-bundle check.

Detekt enables the complexity rules and `WildcardImport`. `LongMethod` allows at most 230 lines;
`WukkiApp` is intentionally absent from the baseline so growing it beyond that limit fails the build.

The module-specific detekt and ktlint baselines contain findings that existed when the gates were
introduced. Do not regenerate a baseline for routine changes. Update it only when an existing
finding is fixed or a rule change is reviewed separately.

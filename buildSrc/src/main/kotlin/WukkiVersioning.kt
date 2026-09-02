import org.gradle.api.GradleException
import org.gradle.api.Project
import java.time.LocalDate
import java.time.ZoneOffset

data class WukkiVersionInfo(
    val displayVersion: String,
    val buildId: String,
    val packageVersion: String,
    val androidVersionCode: Int,
    val buildDate: LocalDate,
    val runNumber: Int
)

object WukkiVersioning {
    private const val MAX_PACKAGE_RUN_NUMBER = 65_535
    private const val MAX_ANDROID_RUN_NUMBER = 9_999
    private const val MAX_ANDROID_VERSION_CODE = 2_100_000_000
    private val shaPattern = Regex("^[0-9a-fA-F]{8,40}$")

    fun calculate(buildDate: LocalDate, commitSha: String, runNumber: Int): WukkiVersionInfo {
        require(buildDate.year in 2000..2099) { "Build year must be between 2000 and 2099." }
        require(shaPattern.matches(commitSha)) { "Git commit must contain 8 to 40 hexadecimal characters." }
        require(runNumber in 1..MAX_PACKAGE_RUN_NUMBER) {
            "Build run number must be between 1 and $MAX_PACKAGE_RUN_NUMBER."
        }

        val shortYear = buildDate.year % 100
        val dayOfYear = buildDate.dayOfYear
        val buildId = commitSha.take(8).lowercase()
        val displayVersion = "%02d.%03d.%s".format(shortYear, dayOfYear, buildId)
        val dayGroup = (dayOfYear - 1) / 2
        val packageVersion = "$shortYear.$dayGroup.$runNumber"
        require(runNumber <= MAX_ANDROID_RUN_NUMBER) {
            "Android release run number must not exceed $MAX_ANDROID_RUN_NUMBER."
        }
        val androidVersionCode = shortYear * 10_000_000 + dayOfYear * 10_000 + runNumber
        require(androidVersionCode in 1..MAX_ANDROID_VERSION_CODE) {
            "Android versionCode $androidVersionCode is outside the supported range."
        }
        return WukkiVersionInfo(
            displayVersion = displayVersion,
            buildId = buildId,
            packageVersion = packageVersion,
            androidVersionCode = androidVersionCode,
            buildDate = buildDate,
            runNumber = runNumber
        )
    }

    fun resolve(project: Project): WukkiVersionInfo {
        val providers = project.providers
        val date = providers.gradleProperty("wukkiBuildDate").orNull
            ?.let(LocalDate::parse)
            ?: LocalDate.now(ZoneOffset.UTC)
        val sha = providers.gradleProperty("wukkiGitSha").orNull
            ?: providers.environmentVariable("GITHUB_SHA").orNull
            ?: providers.exec {
                workingDir(project.rootDir)
                commandLine("git", "rev-parse", "HEAD")
            }.standardOutput.asText.get().trim()
        val runNumber = providers.gradleProperty("wukkiRunNumber").orNull
            ?: providers.environmentVariable("GITHUB_RUN_NUMBER").orNull
            ?: "1"
        val calculated = try {
            calculate(date, sha, runNumber.toInt())
        } catch (exception: IllegalArgumentException) {
            throw GradleException("Invalid Wukki version metadata: ${exception.message}", exception)
        }

        fun checkedOverride(name: String, expected: String): String {
            val supplied = providers.gradleProperty(name).orNull ?: return expected
            if (supplied != expected) {
                throw GradleException("-$name=$supplied does not match calculated value $expected.")
            }
            return supplied
        }

        return calculated.copy(
            displayVersion = checkedOverride("wukkiVersion", calculated.displayVersion),
            buildId = checkedOverride("wukkiBuild", calculated.buildId),
            packageVersion = checkedOverride("wukkiPackageVersion", calculated.packageVersion),
            androidVersionCode = checkedOverride(
                "wukkiVersionCode",
                calculated.androidVersionCode.toString()
            ).toInt()
        )
    }
}

import org.gradle.api.Plugin
import org.gradle.api.Project

class WukkiQualityConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) =
        with(project.pluginManager) {
            apply("dev.detekt")
            apply("org.jlleitschuh.gradle.ktlint")
        }
}

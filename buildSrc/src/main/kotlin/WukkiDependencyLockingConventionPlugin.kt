import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.LockMode

class WukkiDependencyLockingConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) =
        project.dependencyLocking {
            lockAllConfigurations()
            lockMode.set(LockMode.STRICT)
        }
}

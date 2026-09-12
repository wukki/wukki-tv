plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test-junit5"))
}

gradlePlugin {
    plugins {
        register("wukkiQuality") {
            id = "wukki.quality"
            implementationClass = "WukkiQualityConventionPlugin"
        }
        register("wukkiDependencyLocking") {
            id = "wukki.dependency-locking"
            implementationClass = "WukkiDependencyLockingConventionPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

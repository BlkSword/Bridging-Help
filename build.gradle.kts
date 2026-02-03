// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.8.0" apply false
    id("com.android.library") version "8.8.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
}

// Force latest annotations version across all subprojects to avoid duplicates
subprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains:annotations:24.0.0")
        }
        // Exclude old IntelliJ annotations (replaced by JetBrains annotations)
        exclude(group = "com.intellij", module = "annotations")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

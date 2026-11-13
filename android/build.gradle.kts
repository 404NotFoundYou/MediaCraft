buildscript {
    extra["aves_useCrashlytics"] = gradle.startParameter.taskNames.any { it.contains("play", ignoreCase = true) }

    println("Tasks=${gradle.startParameter.taskNames}")
    println("Extra=\n${extra.properties.entries.map { kv -> "  ${kv.key}=${kv.value}" }.sorted().joinToString("\n")}")

    // conditional dependencies cannot be moved to the static `plugins` block
    if (rootProject.extra["aves_useCrashlytics"] as Boolean) {
        dependencies {
            // GMS & Firebase Crashlytics (used by some flavors only)
            classpath(libs.google.gms)
            classpath(libs.google.firebase.crashlytics)
        }
    }
}

plugins {
    alias(libs.plugins.reproducible.builds)
}

val javaCompilerArgs = listOf("-Xlint:unchecked", "-Xlint:deprecation")
allprojects {
    apply(plugin = "org.gradlex.reproducible-builds")

    gradle.projectsEvaluated {
        println("Configure $project JavaCompile tasks with compilerArgs=$javaCompilerArgs")
        tasks.withType<JavaCompile> {
            options.compilerArgs.addAll(javaCompilerArgs)
        }
    }
}

val newBuildDir: Directory = rootProject.layout.buildDirectory.dir("../../build").get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

// Baidu 4.0.2 declares API 35, but the shared Flutter lifecycle plugin needs 36+.
subprojects {
    if (name == "flutter_baidu_mapapi_map") {
        plugins.withId("com.android.library") {
            extensions.configure<com.android.build.api.variant.LibraryAndroidComponentsExtension> {
                finalizeDsl { it.compileSdk = 37 }
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

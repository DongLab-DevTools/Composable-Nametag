plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.vanniktech.maven.publish)
}

// included build는 루트 gradle.properties를 자동으로 읽지 않으므로 직접 파싱
val rootProps = file("../gradle.properties").readLines()
    .filter { it.contains("=") && !it.trimStart().startsWith("#") }
    .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
val libGroup = rootProps["GROUP"] ?: "io.github.dongx0915.composable.nametag"
val libVersion = rootProps["VERSION"] ?: "0.0.1"

group = libGroup
version = libVersion

kotlin {
    jvmToolchain(17)
}

val generateVersionFile = tasks.register("generateVersionFile") {
    val outputDir = layout.buildDirectory.dir("generated/version")
    val version = libVersion
    outputs.dir(outputDir)
    inputs.property("version", version)
    doLast {
        val dir = outputDir.get().asFile.resolve("com/donglab/compose/debug/gradle")
        dir.mkdirs()
        dir.resolve("BuildConfig.kt").writeText(
            """
            package com.donglab.compose.debug.gradle

            internal object BuildConfig {
                const val GROUP = "$libGroup"
                const val VERSION = "$version"
            }
            """.trimIndent()
        )
    }
}

sourceSets["main"].java.srcDir(
    generateVersionFile.map { it.outputs.files.singleFile }
)

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:${libs.versions.kotlin.get()}")
}

gradlePlugin {
    plugins {
        create("composeDebugOverlay") {
            id = "io.github.dongx0915.composable.nametag"
            implementationClass = "com.donglab.compose.debug.gradle.ComposeDebugOverlayPlugin"
            displayName = "Composable-Nametag"
            description = "Kotlin Compiler Plugin that displays @Composable function names on screen for debugging"
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = libGroup,
        artifactId = "composable-nametag-gradle",
        version = libVersion,
    )

    pom {
        name.set("Composable-Nametag — Gradle Plugin")
        description.set("Gradle plugin that auto-registers the Composable-Nametag compiler plugin and runtime")
        url.set("https://github.com/DongLab-DevTools/Composable-Nametag")
        inceptionYear.set("2025")

        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("dongx0915")
                name.set("Donghyeon Kim")
                email.set("donghyeon0915@gmail.com")
            }
        }

        scm {
            url.set("https://github.com/DongLab-DevTools/Composable-Nametag")
            connection.set("scm:git:git://github.com/DongLab-DevTools/Composable-Nametag.git")
            developerConnection.set("scm:git:ssh://git@github.com/DongLab-DevTools/Composable-Nametag.git")
        }
    }

    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
}

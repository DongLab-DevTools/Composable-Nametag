plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.maven.publish)
}

val targetKotlinVersion: String = findProperty("targetKotlinVersion") as? String
    ?: libs.versions.kotlin.get()

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$targetKotlinVersion")
}

kotlin {
    jvmToolchain(17)

    sourceSets.main {
        kotlin.srcDir(
            when {
                targetKotlinVersion.startsWith("2.3") -> "src/main/kotlin-2.3"
                else -> "src/main/kotlin-2.1"
            }
        )
    }
}

mavenPublishing {
    coordinates(
        groupId = property("GROUP") as String,
        artifactId = "composable-nametag-compiler",
        version = "$targetKotlinVersion-${property("VERSION") as String}",
    )

    pom {
        name.set("Composable-Nametag — Compiler")
        description.set("Kotlin Compiler Plugin that displays @Composable function names on screen for debugging")
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
    if (providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY") ?: return@maven}")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

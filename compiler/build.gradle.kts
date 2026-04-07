plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.maven.publish)
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
}

kotlin {
    jvmToolchain(21)
}

mavenPublishing {
    coordinates(
        groupId = property("GROUP") as String,
        artifactId = "composable-nametag-compiler",
        version = property("VERSION") as String,
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
    signAllPublications()
}

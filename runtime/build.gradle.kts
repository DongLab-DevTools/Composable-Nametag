plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    namespace = "com.donglab.compose.debug.runtime"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}

mavenPublishing {
    coordinates(
        groupId = property("GROUP") as String,
        artifactId = "composable-nametag-runtime",
        version = property("VERSION") as String,
    )

    pom {
        name.set("Composable-Nametag — Runtime")
        description.set("Runtime library that provides debug overlay UI for displaying @Composable function names")
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

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
}

val libGroup = providers.gradleProperty("GROUP").getOrElse("com.donglab.compose.debug")
val libVersion = providers.gradleProperty("VERSION").getOrElse("1.0.0")

group = libGroup
version = libVersion

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:${libs.versions.kotlin.get()}")
}

gradlePlugin {
    plugins {
        create("composeDebugOverlay") {
            id = "com.donglab.compose.debug.overlay"
            implementationClass = "com.donglab.compose.debug.gradle.ComposeDebugOverlayPlugin"
            displayName = "Composable-Nametag"
            description = "Kotlin Compiler Plugin that displays @Composable function names on screen for debugging"
        }
    }
}

publishing {
    publications {
        // java-gradle-plugin이 자동 생성하는 publication들에 POM 메타데이터 추가
        withType<MavenPublication>().configureEach {
            pom {
                name.set("Composable-Nametag — Gradle Plugin")
                description.set("Gradle plugin that auto-registers the Composable-Nametag compiler plugin and runtime")
                url.set("https://github.com/DongLab-DevTools/Composable-Nametag")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("dongx0915")
                        name.set("Dong")
                        url.set("https://github.com/dongx0915")
                    }
                }

                scm {
                    url.set("https://github.com/DongLab-DevTools/Composable-Nametag")
                    connection.set("scm:git:git://github.com/DongLab-DevTools/Composable-Nametag.git")
                    developerConnection.set("scm:git:ssh://github.com/DongLab-DevTools/Composable-Nametag.git")
                }
            }
        }

        // Gradle Plugin Marker 외에 별도 publication 추가 (Maven Central용)
        create<MavenPublication>("maven") {
            groupId = libGroup
            artifactId = "compose-debug-overlay-gradle"
            version = libVersion
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "mavenCentral"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = providers.gradleProperty("mavenCentralUsername").getOrElse("")
                password = providers.gradleProperty("mavenCentralPassword").getOrElse("")
            }
        }
    }
}

signing {
    sign(publishing.publications)
}

// signing 자격증명이 없으면 서명 태스크를 건너뛰기 (mavenLocal 배포 시)
tasks.withType<Sign>().configureEach {
    onlyIf {
        project.hasProperty("signing.keyId")
    }
}

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

/**
 * Maven Central + mavenLocal 공통 Publishing 설정.
 *
 * 각 모듈의 build.gradle.kts에서:
 * ```
 * configurePublishing(artifactId = "compose-debug-overlay-compiler")
 * ```
 * 로 호출하면 POM 메타데이터, signing, repository가 일괄 설정된다.
 *
 * ## mavenLocal 배포 (signing 불필요)
 * ```
 * ./gradlew publishToMavenLocal
 * ```
 *
 * ## Maven Central 배포 (signing 필수)
 * ~/.gradle/gradle.properties에 signing.*, mavenCentral* 설정 후:
 * ```
 * ./gradlew publishAllPublicationsToMavenCentralRepository
 * ```
 */
fun Project.configurePublishing(artifactId: String) {
    val libGroup = property("GROUP") as String
    val libVersion = property("VERSION") as String

    this.group = libGroup
    this.version = libVersion

    configure<PublishingExtension> {
        publications.withType<MavenPublication>().configureEach {
            groupId = libGroup
            this.artifactId = artifactId
            this.version = libVersion

            pom {
                name.set("Composable-Nametag — $artifactId")
                description.set("Kotlin Compiler Plugin that displays @Composable function names on screen for debugging")
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

        repositories {
            // Maven Central (Sonatype OSSRH Staging)
            maven {
                name = "mavenCentral"
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = findProperty("mavenCentralUsername") as? String ?: ""
                    password = findProperty("mavenCentralPassword") as? String ?: ""
                }
            }
        }
    }

    // GPG Signing — Maven Central 필수, mavenLocal에서는 건너뛰기
    plugins.withId("signing") {
        configure<SigningExtension> {
            val publishing = extensions.getByType(PublishingExtension::class.java)
            sign(publishing.publications)
        }

        // signing.keyId가 설정되어 있지 않으면 서명 태스크 건너뛰기
        // → mavenLocal 배포 시 GPG 없이 동작
        tasks.withType<Sign>().configureEach {
            onlyIf { project.hasProperty("signing.keyId") }
        }
    }
}

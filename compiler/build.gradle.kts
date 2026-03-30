plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    signing
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

configurePublishing(artifactId = "compose-debug-overlay-compiler")

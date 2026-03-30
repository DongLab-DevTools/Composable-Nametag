plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "com.donglab.compose.debug"
version = "1.0.0"

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.donglab.compose.debug"
            artifactId = "compose-debug-overlay-compiler"
            version = "1.0.0"
            from(components["java"])
        }
    }
}

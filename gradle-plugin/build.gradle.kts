plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.donglab.compose.debug"
version = "1.0.0"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:${libs.versions.kotlin.get()}")
}

gradlePlugin {
    plugins {
        create("composeDebugOverlay") {
            id = "com.donglab.compose.debug.overlay"
            implementationClass = "com.donglab.compose.debug.gradle.ComposeDebugOverlayPlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.donglab.compose.debug"
            artifactId = "compose-debug-overlay-gradle"
            version = "1.0.0"
            from(components["java"])
        }
    }
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "gradle-plugin"

// 루트 프로젝트의 gradle.properties에서 GROUP, VERSION을 읽기 위해
// Gradle은 included build의 상위 디렉토리 gradle.properties를 자동으로 읽지 않으므로
// gradle-plugin/gradle.properties에 심링크하거나, settings에서 직접 로드한다.

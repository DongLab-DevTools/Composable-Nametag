plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("io.github.dongx0915.composable.nametag")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.donglab.compose.kcp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.donglab.compose.kcp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
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

// 샘플 앱은 배포본 대신 로컬 :runtime / :compiler 모듈을 바로 사용 (mavenLocal 배포 불필요)
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        val group = property("GROUP") as String
        substitute(module("$group:composable-nametag-runtime")).using(project(":runtime"))
        substitute(module("$group:composable-nametag-compiler")).using(project(":compiler"))
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

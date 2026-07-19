import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.objectbox)
    id("maven-publish")
}

group = "com.github.raghava-indra-kj"
version = "1.0.1"

android {
    namespace = "com.github.raghavaindrakj.mediapipevision"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    publishing {
        singleVariant("release")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        targetSdk = 37
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.objectbox.android)
    implementation(libs.androidx.exifinterface)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.raghava-indra-kj"
            artifactId = "mediapipe-vision"
            version = "1.0.1"
            afterEvaluate { from(components["release"]) }
        }
    }
}

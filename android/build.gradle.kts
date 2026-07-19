import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.objectbox)
    id("maven-publish")
}

group = "com.github.raghava-indra-kj"
version = "1.0.0"

android {
    namespace = "com.github.raghavaindrakj.mediapipevision"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    // Exposes the release variant as a component JitPack (and any other Maven consumer) can publish.
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

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// Pins the exact Maven coordinates JitPack (or any Maven consumer) publishes this module under —
// without this, JitPack would derive a coordinate from the repo/module name instead.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.raghava-indra-kj"
            artifactId = "mediapipe-vision"
            version = "1.0.0"
            afterEvaluate { from(components["release"]) }
        }
    }
}

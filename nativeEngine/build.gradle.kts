import gobley.gradle.GobleyHost
import gobley.gradle.Variant
import gobley.gradle.cargo.dsl.android
import gobley.gradle.cargo.dsl.jvm
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.gobleyCargo)
    alias(libs.plugins.gobleyUniffi)
    kotlin("plugin.atomicfu") version "2.4.20-Beta2"
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "dev.anthonyhfm.amethyst.nativeengine"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

cargo {
    packageDirectory = layout.projectDirectory.dir("rust")
    jvmVariant = Variant(providers.gradleProperty("amethyst.native.variant").getOrElse("debug"))

    builds.jvm {
        embedRustLibrary = rustTarget == GobleyHost.current.rustTarget
    }

    builds.android {
        // cpal's Android backend (oboe-rs) needs AAudio and the shared libc++ runtime at runtime.
        dynamicLibraries.addAll("aaudio", "c++_shared")
    }
}

uniffi {
    generateFromLibrary {
        namespace = "amethyst_native_engine"
        packageName = "dev.anthonyhfm.amethyst.nativeengine"
    }
}

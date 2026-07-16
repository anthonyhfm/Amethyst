import gobley.gradle.GobleyHost
import gobley.gradle.Variant
import gobley.gradle.cargo.dsl.jvm
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.gobleyCargo)
    alias(libs.plugins.gobleyUniffi)
    kotlin("plugin.atomicfu") version "2.4.0"
}

kotlin {
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

cargo {
    packageDirectory = layout.projectDirectory.dir("rust")
    jvmVariant = Variant(providers.gradleProperty("amethyst.native.variant").getOrElse("debug"))

    builds.jvm {
        embedRustLibrary = rustTarget == GobleyHost.current.rustTarget
    }
}

uniffi {
    generateFromLibrary {
        namespace = "amethyst_native_engine"
        packageName = "dev.anthonyhfm.amethyst.nativeengine"
    }
}

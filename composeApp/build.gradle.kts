import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.compose.hot-reload") version "1.0.0-beta02"
    id("org.jetbrains.kotlinx.atomicfu") version "0.29.0"
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting
        
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.ktmidi)

            runtimeOnly(libs.koin.core)
            runtimeOnly(libs.koin.compose)
            runtimeOnly(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)

            implementation(libs.colorpicker.compose)
            implementation(libs.reorderable)

            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.filekit.core)
            implementation(libs.dropdown)
            kotlin("stdlib")
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs) {
                exclude(compose.material)
            }

            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktmidi.jvm.desktop)
            implementation(libs.coremidi4j)
            implementation(libs.flatlaf)

            val lwjglVersion = "3.3.3"
            val lwjglPlatforms = listOf("natives-windows", "natives-linux", "natives-macos", "natives-macos-arm64")

            // LWJGL Core
            implementation("org.lwjgl:lwjgl:$lwjglVersion")
            implementation("org.lwjgl:lwjgl-openal:$lwjglVersion")
            implementation("org.lwjgl:lwjgl-stb:$lwjglVersion")

            // Natives für alle Plattformen
            lwjglPlatforms.forEach { platform ->
                runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$platform")
                runtimeOnly("org.lwjgl:lwjgl-openal:$lwjglVersion:$platform")
                runtimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:$platform")
            }
        }
    }
}

android {
    namespace = "dev.anthonyhfm.amethyst"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.anthonyhfm.amethyst"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "dev.anthonyhfm.amethyst.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            macOS {
                iconFile.set(project.file("../icons/amethyst_macos.icns"))
            }

            windows {
                iconFile.set(project.file("../icons/amethyst_windows.ico"))
            }

            linux {
                modules("jdk.security.auth") // Required for FileKit

                iconFile.set(project.file("../icons/amethyst_linux.png"))
            }

            packageName = "Amethyst"
            packageVersion = "1.0.0"
        }
    }
}

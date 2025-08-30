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
    id("org.jetbrains.kotlinx.atomicfu") version "0.29.0"
    id("org.jetbrains.compose.hot-reload") version "1.0.0-beta04"
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
            implementation("org.jetbrains.compose.material3:material3:1.9.0-alpha04")
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
            implementation("io.github.vinceglb:filekit-core:0.10.0")
            implementation("io.github.vinceglb:filekit-dialogs:0.10.0")
            implementation(libs.dropdown)
            implementation("com.mohamedrejeb.dnd:compose-dnd:0.4.0")
            implementation("io.github.pdvrieze.xmlutil:core:0.91.2")
            implementation("io.github.pdvrieze.xmlutil:serialization:0.91.2")
            implementation("io.github.pdvrieze.xmlutil:serialutil:0.91.2")
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
            implementation("io.github.vyfor:kpresence:0.6.5")

            // LWJGL OpenAL für Low-Latency Audio
            implementation("org.lwjgl:lwjgl:3.3.3")
            implementation("org.lwjgl:lwjgl-openal:3.3.3")
            runtimeOnly("org.lwjgl:lwjgl:3.3.3:natives-macos")
            runtimeOnly("org.lwjgl:lwjgl:3.3.3:natives-macos-arm64")
            runtimeOnly("org.lwjgl:lwjgl-openal:3.3.3:natives-macos")
            runtimeOnly("org.lwjgl:lwjgl-openal:3.3.3:natives-macos-arm64")
            runtimeOnly("org.lwjgl:lwjgl:3.3.3:natives-windows")
            runtimeOnly("org.lwjgl:lwjgl:3.3.3:natives-linux")
            runtimeOnly("org.lwjgl:lwjgl-openal:3.3.3:natives-windows")
            runtimeOnly("org.lwjgl:lwjgl-openal:3.3.3:natives-linux")

            // Audio Decoding Libraries
            implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
            implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")
            implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
            implementation("org.jflac:jflac-codec:1.5.2")
            implementation("com.github.stephenc.java-iso-tools:java-iso-tools-parent:2.0.1")
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
            packageName = "Amethyst"
            packageVersion = "1.0.0"

            targetFormats(TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Deb)

            macOS {
                iconFile.set(project.file("../icons/amethyst_macos.icns"))
                dockName = "Amethyst"
            }

            windows {
                iconFile.set(project.file("../icons/amethyst_windows.ico"))
                menu = true
                shortcut = true
            }

            linux {
                modules("jdk.security.auth") // Required for FileKit

                iconFile.set(project.file("../icons/amethyst_linux.png"))
            }
        }
    }
}

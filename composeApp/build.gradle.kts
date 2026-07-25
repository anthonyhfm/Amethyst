import io.github.kdroidfilter.nucleus.desktop.application.dsl.DmgContentType
import io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization") version "2.4.0"
    id("org.jetbrains.kotlinx.atomicfu") version "0.29.0"
    alias(libs.plugins.sentryKmp)
    alias(libs.plugins.nucleus)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    val iosFrameworkBundleId = "dev.anthonyhfm.amethyst.composeapp"

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            binaryOption("bundleId", iosFrameworkBundleId)
            isStatic = true
        }
    }

    jvm("desktop") {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.jetbrains.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.0")

            implementation(libs.reorderable)

            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.sentry.kotlin.multiplatform)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings.no.arg)
            implementation("io.github.vinceglb:filekit-core:0.10.0")
            implementation("io.github.vinceglb:filekit-dialogs:0.10.0")
            implementation("io.github.pdvrieze.xmlutil:core:1.0.0-rc1")
            implementation("io.github.pdvrieze.xmlutil:serialization:1.0.0-rc1")
            implementation("io.github.pdvrieze.xmlutil:serialutil:1.0.0-rc1")
            implementation("com.squareup.okio:okio:3.9.0")
            implementation("com.composables:composeunstyled:1.49.6")
            implementation("com.composables:icons-lucide-cmp:2.2.1")

            implementation("com.mikepenz:multiplatform-markdown-renderer:0.40.2")
            implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.40.2")

            // Ktor WebSocket Client (commonMain — used by LanConnectProvider client mode)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)

            kotlin("stdlib")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs) {
                exclude(compose.material)
            }

            implementation(projects.nativeEngine)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.flatlaf)
            implementation("io.github.vyfor:kpresence:0.6.5")

            // Ktor WebSocket Server + CIO engine (JVM/Desktop only)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
            // Nucleus runtime
            implementation(libs.nucleus.core.runtime)
            implementation(libs.nucleus.aot.runtime)
            implementation(libs.nucleus.updater.runtime)
            implementation(libs.nucleus.native.http)
            implementation(libs.nucleus.taskbar.progress)
            implementation(libs.nucleus.decorated.window)
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
            isMinifyEnabled = true
            isShrinkResources = true
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
}

dependencies {
    debugImplementation(compose.uiTooling)
}

nucleus.application {
    mainClass = "dev.anthonyhfm.amethyst.MainKt"

    buildTypes {
        release {
            proguard {
                isEnabled = false
            }
        }
    }
    
    jvmArgs += listOf(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    )

    nativeDistributions {
        packageName = "Amethyst"
        packageVersion = "1.0.0"
        homepage = "https://amethyst.anthonyhfm.dev"

        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.AppImage)

        includeAllModules = true

        macOS {
            iconFile.set(project.file("../icons/amethyst_macos.icns"))

            macOsSdkVersion = null

            dmg {
                title = "Amethyst Installer"
                background.set(project.file("../icons/amethyst_dmg.png"))
                iconSize = 128
                iconTextSize = 12

                content(x = 143, y = 140, type = DmgContentType.File, name = "Amethyst.app")
                content(x = 143, y = 455, type = DmgContentType.Link, path = "/Applications")

            }
        }

        windows {
            iconFile.set(project.file("../icons/amethyst_windows.ico"))
        }

        linux {
            modules("jdk.security.auth")
            iconFile.set(project.file("../icons/amethyst_linux.png"))
            debMaintainer = "contact@anthonyhfm.dev"
        }

        fileAssociation(
            mimeType = "application/x-amethyst",
            extension = "ame",
            description = "Amethyst Project File",
            macOSIconFile = project.file("../icons/ame_file.icns"),
            windowsIconFile = project.file("../icons/ame_file.ico"),
            linuxIconFile = project.file("../icons/ame_file.png")
        )
    }
}

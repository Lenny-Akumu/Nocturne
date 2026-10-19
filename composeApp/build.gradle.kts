import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val fxPlatform: String = System.getProperty("os.name").lowercase().let {
    when {
        it.contains("win") -> "win"
        it.contains("mac") -> if (System.getProperty("os.arch") == "aarch64") "mac-aarch64" else "mac"
        else -> "linux"
    }
}

val appVersion: String = Regex("VERSION = \"(.+?)\"")
    .find(file("src/commonMain/kotlin/app/nocturne/BuildInfo.kt").readText())!!.groupValues[1]
val appVersionCode: Int = appVersion.split(".").map { it.toInt() }.let { (a, b, c) -> a * 10000 + b * 100 + c }

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=kotlin.io.encoding.ExperimentalEncodingApi",
        )
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.icons)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.ktor.core)
            implementation(libs.ktor.negotiation)
            implementation(libs.ktor.json)
            implementation(libs.coil.compose)
            implementation(libs.materialkolor)
        }

        androidMain {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                implementation(libs.coroutines.android)
                implementation(libs.ktor.okhttp)
                implementation(libs.coil.okhttp)
                implementation(libs.okhttp)
                implementation(libs.newpipe)
                implementation(libs.activity.compose)
                implementation(libs.core.ktx)
                implementation(libs.media3.exoplayer)
                implementation(libs.media3.hls)
                implementation(libs.media3.session)
                implementation(libs.media3.datasource)
                implementation(libs.media3.ui)
                implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
                implementation("androidx.fragment:fragment-ktx:1.8.9")
            }
        }

        desktopMain.kotlin.srcDir("src/jvmShared/kotlin")
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.coroutines.swing)
            implementation(libs.ktor.okhttp)
            implementation(libs.coil.okhttp)
            implementation(libs.okhttp)
            implementation(libs.newpipe)
            implementation(libs.vlcj)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation("com.google.zxing:core:3.5.3")
            listOf("base", "graphics", "controls", "media", "web", "swing").forEach {
                implementation("org.openjfx:javafx-$it:${libs.versions.javafx.get()}:$fxPlatform")
            }
        }
    }
}

android {
    namespace = "app.nocturne"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.nocturne"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    packaging {
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/INDEX.LIST", "META-INF/DEPENDENCIES") }
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("NOCTURNE_KEYSTORE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("NOCTURNE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NOCTURNE_KEY_ALIAS")
                keyPassword = System.getenv("NOCTURNE_KEY_PASSWORD")
            } else {
                storeFile = file("nocturne-release.jks")
                storePassword = "nocturne"
                keyAlias = "nocturne"
                keyPassword = "nocturne"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)
}

compose.desktop {
    application {
        mainClass = "app.nocturne.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Nocturne"
            packageVersion = appVersion
            description = "Nocturne music player"
            vendor = "Nocturne"
            includeAllModules = true
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            windows {
                iconFile.set(project.file("icons/nocturne.ico"))
                menuGroup = "Nocturne"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f1d2a4e-8c3b-4f7a-9e21-5b0c7d3e9a14"
            }
        }
    }
}

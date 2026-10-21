plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Numer builda z GitHub Actions => rosnący versionCode, dzięki czemu nowe APK aktualizuje stare
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "pl.maja.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.maja.game"
        minSdk = 21
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.$runNumber"
    }

    // Ten sam klucz przy każdym buildzie (na runnerach GitHub klucz debug byłby za każdym razem inny)
    signingConfigs {
        getByName("debug") {
            storeFile = file("maja-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // JEDYNE źródło gry: katalog web/ w korzeniu repo trafia do assets w APK
    sourceSets {
        getByName("main") {
            assets.srcDir(rootProject.file("web"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

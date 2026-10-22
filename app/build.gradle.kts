plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "nl.zangcoach.studio"
    compileSdk = 35

    defaultConfig {
        // Zelfde naam als de oude app: zo installeert hij erover en blijven je nummers bewaard.
        applicationId = "nl.zangcoach.studio"
        minSdk = 29
        targetSdk = 34
        versionCode = 33
        versionName = "3.9"
    }

    signingConfigs {
        create("release") {
            // De sleutel komt uit de geheimen van GitHub (zie .github/workflows/build.yml), nooit uit de code.
            val ks = System.getenv("KEYSTORE_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Zonder sleutel (geheimen nog niet ingesteld) wordt een niet-ondertekende testversie gebouwd.
            if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // targetSdk 34 is een bewuste keuze (zie OVERDRACHT.md); lint mag de release daarom niet tegenhouden.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}

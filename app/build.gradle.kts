import java.util.Properties

plugins {
    id("com.android.application")
}

val releasePropertiesFile = rootProject.file(".signing/keystore.properties")
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.isFile) {
        releasePropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.tabletwake"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tabletwake"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releasePropertiesFile.isFile) {
            create("release") {
                storeFile = rootProject.file(".signing/${releaseProperties["storeFile"]}")
                storePassword = releaseProperties["storePassword"] as String
                keyAlias = releaseProperties["keyAlias"] as String
                keyPassword = releaseProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // Temporaneamente disattivati: la stabilità sui firmware OEM viene prima
            // della dimensione dell'APK. Verranno riattivati dopo i test sul tablet.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")

    testImplementation("junit:junit:4.13.2")
}

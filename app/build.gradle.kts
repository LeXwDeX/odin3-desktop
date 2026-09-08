plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
}

// Stable version tags map to increasing Android versions, independent of CI reruns.
val releaseVersion = providers.gradleProperty("releaseVersion").getOrElse("0.1.2")
require(releaseVersion.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))) {
    "releaseVersion must be MAJOR.MINOR.PATCH without leading zeroes"
}
val versionParts = releaseVersion.split('.').map { it.toInt() }
require(versionParts[0] in 0..2099 && versionParts[1] in 0..999 && versionParts[2] in 0..999)
val releaseVersionCode = versionParts[0] * 1_000_000 + versionParts[1] * 1_000 + versionParts[2] + 1

android {
    namespace = "com.odin.desktop"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.odin.desktop"
        minSdk = 29
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersion
        resourceConfigurations += listOf("en", "b+zh+Hans", "b+zh+Hant", "ja")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets.getByName("test").resources.srcDir("schemas")
    bundle {
        language {
            // CONFIG must be able to switch to any supported language while offline.
            enableSplit = false
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.google.material)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}

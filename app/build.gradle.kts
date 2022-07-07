plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

val compose_version: String by project
val accompanist_version: String by project

android {
    compileSdk = 32

    defaultConfig {
        applicationId = "com.example.rovermap_compose"
        minSdk = 24
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isShrinkResources = false
            isMinifyEnabled = false
            isJniDebuggable = true
        }
        release {
            // use the debug signing config for the release build
            // -> don't do this in production!
            signingConfig = signingConfigs.getByName("debug")

            isShrinkResources = true
            isMinifyEnabled = true
            isJniDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose  = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.2.0"
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.0")
    implementation("androidx.activity:activity-compose:1.5.0")
    implementation("androidx.compose.ui:ui:$compose_version")
    implementation("androidx.compose.material:material:$compose_version")
    implementation("androidx.compose.material:material-icons-extended:$compose_version")
    implementation("androidx.compose.ui:ui-tooling-preview:$compose_version")
    debugImplementation("androidx.compose.ui:ui-tooling:$compose_version")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$compose_version")
    implementation("com.google.accompanist:accompanist-permissions:$accompanist_version")
    implementation("com.google.accompanist:accompanist-systemuicontroller:$accompanist_version")
    implementation("com.google.android.gms:play-services-location:20.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.3")

    implementation(files("libs/rovermapgfx-release.aar"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf(
            "-opt-in=kotlin.RequiresOptIn",
        )
    }
}
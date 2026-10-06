import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "io.github.veritasx1.licida"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.veritasx1.licida"
        // Android 10+: saving to Fotos and the photo picker need no storage permission.
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.2b"
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (keystoreProperties.isNotEmpty()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Tests check the German texts (the source language) – regardless of the computer's locale.
            it.systemProperty("licida.language", (project.findProperty("lang") as String?) ?: "de")
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            // Pictures: ./gradlew testDebugUnitTest -Pshots=/folder
            (project.findProperty("shots") as String?)?.let { folder -> it.systemProperty("licida.shots", folder) }
        }
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

// The translations (locale/<lang>.json in the repository) go into the app's assets.
val copyLocale by tasks.registering(Sync::class) {
    from(rootProject.file("../locale")) { include("*.json") }
    into(layout.buildDirectory.dir("generated/locale/locale"))
}
android.sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/locale"))
tasks.named("preBuild") { dependsOn(copyLocale) }

dependencies {
    // Only Google's AndroidX – no network, analytics or ad libraries (privacy: card 462a919b).
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    // Printing the target goes through Android's own print dialog (LiCida itself stays offline).
    implementation("androidx.print:print:1.0.0")
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

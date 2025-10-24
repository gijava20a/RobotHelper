plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Include all ABIs for emulator compatibility (accept 16KB warning for now)
        // Remove this filter to include x86/x86_64 for emulators
        // ndk {
        //     abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        // }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES"
            )
        }

        // Comment out x86 exclusions to support emulators
        // jniLibs {
        //     excludes += listOf(
        //         "**/x86/**",
        //         "**/x86_64/**"
        //     )
        //     useLegacyPackaging = false
        // }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    implementation("io.agora.rtc:full-sdk:4.5.2")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.android.volley:volley:1.2.1")
    implementation("com.hivemq:hivemq-mqtt-client:1.3.3")

    // Updated ML Kit to latest version with better 16KB support
    implementation("com.google.mlkit:object-detection:17.0.2")

    // Updated CameraX to latest versions
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
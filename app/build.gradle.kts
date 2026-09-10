plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

android {
    namespace = "com.example.scoring"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.scoring"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            isV1SigningEnabled = true
            isV2SigningEnabled = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")

            applicationVariants.all {
                val variant = this
                variant.outputs.all {
                    val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
                    output.outputFileName = "Golden_Duck_v${variant.versionName}.apk"
                }
            }
        }

        debug {
            isDebuggable = true
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
        viewBinding = true
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/proguard/androidx-*.pro",
                "META-INF/proguard/com-google-*.pro",
                "META-INF/proguard/kotlin-*.pro",
                "DebugProbesKt.bin"
            )
        }
    }

    lint {
        disable += listOf(
            "MissingTranslation",
            "ExtraTranslation"
        )
        abortOnError = false
    }
}

dependencies {
    // Firebase - BOM for version management
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.crashlytics)

    // OneSignal
    implementation(libs.onesignal)

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // AndroidX
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.biometric)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Kotlin Standard Library & Extensions
    implementation(libs.kotlin.stdlib)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // JSON & Serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // UI Libraries
    implementation("com.github.jinatonic.confetti:confetti:1.1.2")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.airbnb.android:lottie:6.3.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

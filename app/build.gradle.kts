plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.houvven.impad"
    compileSdk = 36

    defaultConfig {
        applicationId = namespace
        minSdk = 27
        targetSdk = 36
        versionCode = 14
        versionName = "1.2.2"
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.dexkit)
    implementation(libs.kavaref.core)
    implementation(libs.kavaref.extension)
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit)
    testImplementation(libs.libxposed.api)
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.subtitleblocker"
    compileSdk {
        version = release(37)
    }
    signingConfigs {
        create("release") {
            file("D:/key/my key1.jks")
            storePassword = "huihuangerduo"
            keyAlias = "hhed-key"
            keyPassword = "huihuangerduo"
        }
    }
    defaultConfig {
        applicationId = "com.example.subtitleblocker"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "3.0(calico cat)"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
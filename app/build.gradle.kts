plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "org.johnspahr.stuart"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.johnspahr.stuart"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
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
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.positiondev.epublib:epublib-core:3.1") {
        exclude(group = "xmlpull", module = "xmlpull")
        exclude(group = "net.sf.kxml", module = "kxml2")
    }
    implementation("org.jsoup:jsoup:1.16.1")
}
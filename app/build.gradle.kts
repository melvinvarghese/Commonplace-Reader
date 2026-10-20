import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "im.commonplace.reader"
    compileSdk = 37
    compileSdkExtension = 22
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "im.commonplace.reader"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "pdfEngine"
    productFlavors {
        create("androidx") {
            dimension = "pdfEngine"
            applicationIdSuffix = ".androidx"
            versionNameSuffix = "-androidx"
        }
        create("composepdf") {
            dimension = "pdfEngine"
            applicationIdSuffix = ".composepdf"
            versionNameSuffix = "-composepdf"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugarJdkLibs)
    implementation(platform(libs.composeBom))
    implementation(libs.activityCompose)
    implementation(libs.lifecycleViewmodelCompose)
    implementation(libs.lifecycleRuntimeCompose)
    implementation(libs.composeUi)
    implementation(libs.composeUiGraphics)
    implementation(libs.composeUiToolingPreview)
    implementation(libs.composeMaterial3)
    implementation(libs.coreKtx)
    implementation(libs.coroutinesAndroid)
    implementation(libs.readiumNavigator)

    "androidxImplementation"(libs.androidxPdfViewerFragment)
    "composepdfImplementation"(libs.composePdfium)

    testImplementation(libs.junit)

    androidTestImplementation(libs.testExtJunit)
    androidTestImplementation(libs.testRunner)
    androidTestImplementation(libs.espressoCore)
    androidTestImplementation(platform(libs.composeBom))
    androidTestImplementation(libs.composeUiTestJunit4)

    debugImplementation(libs.composeUiTooling)
    debugImplementation(libs.composeUiTestManifest)
}

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

    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("test").java.srcDir("src/androidTest/java/im/commonplace/reader/data")

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
    implementation(libs.serializationJson)
    implementation(libs.roomRuntime)
    implementation(libs.roomKtx)
    ksp(libs.roomCompiler)
    implementation(libs.readiumNavigator)

    "androidxImplementation"(libs.androidxPdfViewerFragment)
    "composepdfImplementation"(libs.composePdfium)

    testImplementation(libs.junit)
    testImplementation(libs.testExtJunit)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.testExtJunit)
    androidTestImplementation(libs.testRunner)
    androidTestImplementation(libs.espressoCore)
    androidTestImplementation(platform(libs.composeBom))
    androidTestImplementation(libs.composeUiTestJunit4)

    debugImplementation(libs.composeUiTooling)
    debugImplementation(libs.composeUiTestManifest)
}

class RoomSchemaArgProvider(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) val schemaDir: File,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("room.schemaLocation=${schemaDir.path}")
}

ksp { arg(RoomSchemaArgProvider(file("schemas"))) }

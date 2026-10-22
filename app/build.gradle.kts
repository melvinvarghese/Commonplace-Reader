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
            buildConfigField("String", "PDF_ENGINE_ID", "\"androidx-pdf\"")
        }
        create("composepdf") {
            dimension = "pdfEngine"
            applicationIdSuffix = ".composepdf"
            versionNameSuffix = "-composepdf"
            buildConfigField("String", "PDF_ENGINE_ID", "\"nucleus-composepdf\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("test").java.srcDir("src/sharedTest/java")
    sourceSets.getByName("androidTest").java.srcDir("src/sharedTest/java")
    sourceSets.getByName("test").java.srcDir("src/androidTest/java/im/commonplace/reader/data")
    sourceSets.getByName("test").java.srcDir("src/androidTest/java/im/commonplace/reader/imports")
    sourceSets.getByName("test").resources.srcDir("schemas")
    sourceSets.getByName("androidTest").assets.srcDir("schemas")

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation("androidx.fragment:fragment-ktx:1.9.0")
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
    implementation(libs.datastorePreferences)
    ksp(libs.roomCompiler)
    implementation(libs.readiumNavigator)
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.4.0")
    implementation("org.jsoup:jsoup:1.23.2")

    "androidxImplementation"(libs.androidxPdfViewerFragment)
    "composepdfImplementation"(libs.composePdfium)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation(libs.testExtJunit)
    testImplementation(libs.robolectric)
    testImplementation(libs.composeUiTestJunit4)
    testImplementation(libs.testRunner)
    testImplementation(libs.espressoCore)

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

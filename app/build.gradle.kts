import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.itermeum.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.itermeum.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // One permanent key for every build (D-21): the Google OAuth client is
    // bound to this key's SHA-1. The keystore is never committed: CI decodes
    // it from a secret, a local build reads keystore.properties. Without
    // either, the default debug key is used and Google Sign-In stays off.
    val keystoreFile = file(System.getenv("ITERMEUM_KEYSTORE") ?: "keystore/itermeum.jks")
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val keystorePassword = System.getenv("ITERMEUM_KEYSTORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")
    val hasItermeumKey = keystoreFile.exists() && keystorePassword != null
    signingConfigs {
        if (hasItermeumKey) {
            create("itermeum") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "itermeum"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        // No applicationIdSuffix on debug: the Google OAuth client is bound to
        // the package name and the app ships as a sideloaded debug APK
        // (docs/DECISIONS.md, D-02).
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
            if (hasItermeumKey) signingConfig = signingConfigs.getByName("itermeum")
        }
        release {
            if (hasItermeumKey) signingConfig = signingConfigs.getByName("itermeum")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Sideloaded onto one known arm64 phone (Xiaomi 13T Pro): no other native ABIs (ML Kit, CameraX).
    defaultConfig { ndk { abiFilters += "arm64-v8a" } }

    lint {
        // Reported in CI as an artifact; not yet a gate (no local SDK to fix a backlog against).
        abortOnError = false
        checkReleaseBuilds = false
        htmlReport = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Robolectric reads merged res + assets (strings, assets/*.json) in unit tests.
    // Committed Room schemas feed MigrationTestHelper: Robolectric reads the variant's
    // merged assets, so they go into the debug variant (not into release).
    sourceSets { getByName("debug").assets.srcDir("$projectDir/schemas") }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showStackTraces = false
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google Sign-In + Drive AppData REST backup.
    implementation(libs.play.services.auth)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.work.compiler)

    // Morning block (section 5.1): QR proof via CameraX + ML Kit, QR generation via ZXing (D-11).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)

    // Section 9: Glance 4×2 home-screen widget (D-17).
    implementation(libs.androidx.glance.appwidget)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.work.testing)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn", "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

// Robolectric's android-all jars come through Gradle (retries, cache) instead of
// Robolectric's own Maven Central download, which hit HTTP 429 in CI. Both SDK
// levels Robolectric 4.14 may pick are copied into one offline directory.
val robolectricJars: Configuration by configurations.creating
dependencies {
    robolectricJars("org.robolectric:android-all-instrumented:14-robolectric-10818077-i7")
    robolectricJars("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
}

val robolectricOfflineDir = layout.buildDirectory.dir("robolectric-jars")
val syncRobolectricJars by tasks.registering(Sync::class) {
    from(robolectricJars)
    into(robolectricOfflineDir)
}

abstract class RobolectricOfflineArgs : CommandLineArgumentProvider {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val dir: DirectoryProperty
    override fun asArguments(): Iterable<String> =
        listOf("-Drobolectric.offline=true", "-Drobolectric.dependency.dir=" + dir.get().asFile.absolutePath)
}

tasks.withType<Test>().configureEach {
    dependsOn(syncRobolectricJars)
    jvmArgumentProviders.add(objects.newInstance<RobolectricOfflineArgs>().apply { dir.set(robolectricOfflineDir) })
}

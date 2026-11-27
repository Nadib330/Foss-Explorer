import java.util.Properties

plugins {
    // Kotlin support is built into the Android Gradle Plugin since AGP 9,
    // so no separate org.jetbrains.kotlin.android plugin is applied.
    alias(libs.plugins.android.application)
}

// Optional release signing: create keystore.properties in the project root with
// storeFile, storePassword, keyAlias and keyPassword. The file is git-ignored.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.rpooi3.explorer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rpooi3.explorer"
        minSdk = 26
        targetSdk = 37

        versionCode = 36
        versionName = "18.8.2"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
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
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        disable += "MissingTranslation"
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources and manifest.
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "512m"
                // Robolectric keeps the files of every test until the run is over (about 1.5 MB
                // per test). They go to the build directory; /tmp is a small RAM disk on some machines.
                it.systemProperty("java.io.tmpdir", it.temporaryDir.absolutePath)
                // 18.0.0 beta 6: the report of a run as JUnit XML too, so that a failure can be read
                // where it happened instead of only in the console.
                it.reports.junitXml.required.set(true)
            }
        }
    }
}

dependencies {
    implementation(libs.material)
    // Already on the classpath through Material at this version; declared because the Kotlin code uses it directly.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.documentfile)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

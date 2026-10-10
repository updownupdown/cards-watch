import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.updownupdown.cards.watch"
    compileSdk = 37

    defaultConfig {
        // The phone app's package: both are one Play listing, the watch on
        // its Wear OS track.
        applicationId = "com.updownupdown.cards"
        minSdk = 30
        targetSdk = 36
        // Bump for every upload. The version code follows it, in a range of
        // its own (36MMmmpp, after targetSdk), as it must never repeat one of
        // the phone's, whose codes EAS counts up from 1.
        versionName = "0.1.0"
        val (major, minor, patch) = versionName!!.split(".").map(String::toInt)
        versionCode = targetSdk!! * 10_000_000 + major * 10_000 + minor * 100 + patch
    }

    // The phone's upload key (from `eas credentials` in the phone app), kept
    // out of the repo in keystore.properties: storeFile, storePassword,
    // keyAlias, keyPassword. Without it the release build is left unsigned.
    val upload = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    signingConfigs {
        if (upload != null) {
            create("upload") {
                storeFile = rootProject.file(upload.getProperty("storeFile"))
                storePassword = upload.getProperty("storePassword")
                keyAlias = upload.getProperty("keyAlias")
                keyPassword = upload.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("upload")
        }
        // Release speed for testing on the watch: shrunk and optimised, not
        // debuggable, signed with the debug key. Debug builds of Compose run
        // many times slower than this.
        create("fast") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    // The cards' rank, suit and back icons, read as SVGs when the app starts.
    sourceSets["main"].assets.srcDir("src/main/card-icons")

    // Only the app's languages (AppLanguage) from its libraries too. Android
    // still names Indonesian "in" here.
    androidResources {
        localeFilters += listOf("en", "fr", "es", "it", "pt", "tr", "in")
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// One profile, measured on a watch, for every build type.
baselineProfile {
    mergeIntoMain = true
}

// StoreScreenshotTest's images, so a test run restored from the build cache
// brings them back too.
tasks.withType<Test>().configureEach {
    outputs.dir(layout.buildDirectory.dir("outputs/store"))
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.wear)
    // Opens a game's picture's page on the phone, from its credit.
    implementation(libs.wear.remote.interactions)
    implementation(libs.profileinstaller)
    // The profile measured on a watch: ./gradlew :app:generateBaselineProfile
    baselineProfile(project(":baselineprofile"))
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.complications)
    implementation(libs.concurrent.futures)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}

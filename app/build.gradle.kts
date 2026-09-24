
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// ---------------------------------------------------------------- secrets
// Injected as env vars by the GitHub Actions workflow, so no key ever lives
// in the source. Local builds may set the same values in keystore.properties.
val aiKey: String = System.getenv("OPENROUTER_API_KEY")
    ?: (project.findProperty("OPENROUTER_API_KEY") as String?)
    ?: ""
val chatUrl: String = System.getenv("OPENROUTER_BASE_URL")
    ?: "https://openrouter.ai/api/v1"

val ksStoreB64: String = System.getenv("KEYSTORE_BASE64") ?: ""
val ksStorePass: String = System.getenv("KEYSTORE_PASSWORD")
    ?: (project.findProperty("KEYSTORE_PASSWORD") as String?) ?: ""
val ksKeyAlias: String = System.getenv("KEY_ALIAS")
    ?: (project.findProperty("KEY_ALIAS") as String?) ?: ""
val ksKeyPass: String = System.getenv("KEY_PASSWORD")
    ?: (project.findProperty("KEY_PASSWORD") as String?) ?: ""

val hasSigning = ksStoreB64.isNotBlank() && ksKeyAlias.isNotBlank()
val releaseKeystore = layout.buildDirectory.file("release.keystore").get().asFile
if (hasSigning) {
    releaseKeystore.parentFile.mkdirs()
    releaseKeystore.writeBytes(Base64.getMimeDecoder().decode(ksStoreB64))
}

android {
    namespace = "com.dastyar.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dastyar.app"
        minSdk = 26
        targetSdk = 34
        // CI sets APP_VERSION (1.0.<run_number>); local builds fall back.
        val appVer = System.getenv("APP_VERSION") ?: "1.0.0"
        versionCode = appVer.split(".").let { v ->
            val maj = v.getOrNull(0)?.toIntOrNull() ?: 1
            val min = v.getOrNull(1)?.toIntOrNull() ?: 0
            val pat = v.getOrNull(2)?.toIntOrNull() ?: 0
            maj * 10000 + min * 100 + pat
        }
        versionName = appVer
        resourceConfigurations += listOf("fa", "en")
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = ksStorePass
                keyAlias = ksKeyAlias
                keyPassword = ksKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "OPENROUTER_API_KEY", "\"$aiKey\"")
            buildConfigField("String", "OPENROUTER_BASE_URL", "\"$chatUrl\"")
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "OPENROUTER_API_KEY", "\"$aiKey\"")
            buildConfigField("String", "OPENROUTER_BASE_URL", "\"$chatUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // DataStore for settings
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager for reminders
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Coil for images
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Vico chart
    implementation("com.patrykandpatrick.vico:compose-m3:1.14.0")
}

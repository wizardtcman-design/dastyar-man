
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.dastyar.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dastyar.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("fa", "en")
    }

    // AI keys: read from GitHub Secrets (injected as env vars by the workflow)
    // or from a local keystore.properties. NEVER committed.
    val aiKey: String = System.getenv("ATRIA_API_KEY")
        ?: (project.findProperty("ATRIA_API_KEY") as String?)
        ?: ""
    val chatUrl: String = System.getenv("ATRIA_BASE_URL")
        ?: "https://api.atria-asi.ai"

    // Release signing: the keystore is decoded from a GitHub Secret at build
    // time. Locally it can come from keystore.properties (never committed).
    val ksProps = java.util.Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val ksStoreB64: String = System.getenv("KEYSTORE_BASE64") ?: ""
    val ksStorePass: String = System.getenv("KEYSTORE_PASSWORD")
        ?: (project.findProperty("KEYSTORE_PASSWORD") as String?) ?: ""
    val ksKeyAlias: String = System.getenv("KEY_ALIAS")
        ?: (project.findProperty("KEY_ALIAS") as String?) ?: ""
    val ksKeyPass: String = System.getenv("KEY_PASSWORD")
        ?: (project.findProperty("KEY_PASSWORD") as String?) ?: ""

    val releaseKeystore = layout.buildDirectory.file("release.keystore").get().asFile
    if (ksStoreB64.isNotBlank()) {
        releaseKeystore.parentFile.mkdirs()
        releaseKeystore.writeBytes(java.util.Base64.getMimeDecoder().decode(ksStoreB64))
    }

    signingConfigs {
        if (ksStoreB64.isNotBlank() && ksKeyAlias.isNotBlank()) {
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
            buildConfigField("String", "ATRIA_API_KEY", "\"$aiKey\"")
            buildConfigField("String", "ATRIA_BASE_URL", "\"$chatUrl\"")
            if (ksStoreB64.isNotBlank() && ksKeyAlias.isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "ATRIA_API_KEY", "\"$aiKey\"")
            buildConfigField("String", "ATRIA_BASE_URL", "\"$chatUrl\"")
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

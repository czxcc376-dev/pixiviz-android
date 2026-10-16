import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val envKeystore: String? = System.getenv("KEYSTORE_BASE64")
val envStorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
val envKeyAlias: String? = System.getenv("KEY_ALIAS")
val envKeyPassword: String? = System.getenv("KEY_PASSWORD")
val hasReleaseKeystore: Boolean =
    listOf(envKeystore, envStorePassword, envKeyAlias, envKeyPassword).all { !it.isNullOrBlank() }

val defaultWorkerUrl: String = System.getenv("WORKER_URL") ?: ""
val defaultAppKey: String = System.getenv("WORKER_ACCESS_KEY") ?: ""

android {
    namespace = "pixiviz.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "pixiviz.mobile"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "DEFAULT_WORKER_URL", "\"$defaultWorkerUrl\"")
        buildConfigField("String", "DEFAULT_APP_KEY", "\"$defaultAppKey\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                val target = File(layout.buildDirectory.asFile.get(), "release-keystore.jks")
                target.parentFile?.mkdirs()
                target.writeBytes(Base64.getDecoder().decode(envKeystore))
                storeFile = target
                storePassword = envStorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)

    implementation(libs.haze)
    implementation(libs.androidx.glance.appwidget)

    testImplementation(libs.junit)
}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.kotlin.serialization)
}

// Stamped per build, so two APKs are never confused with each other.
val buildStamp: String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

android {
  buildFeatures {
    buildConfig = true
  }

  namespace = "com.ember.companion"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.ember.companion"
    minSdk = 24
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"
    // Shown in Settings so a stale install can be told apart from a live bug.
    buildConfigField("String", "BUILD_STAMP", "\"$buildStamp\"")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    debug {
      isMinifyEnabled = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
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
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.webkit)
  implementation(libs.coil.compose)
  implementation(libs.okhttp)
    implementation("org.jsoup:jsoup:1.16.1")
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  debugImplementation(libs.androidx.compose.ui.tooling)

  ksp(libs.androidx.room.compiler)

  testImplementation(libs.junit)
  // The android.jar shipped for unit tests only stubs org.json, so card
  // serialisation cannot be exercised without a real implementation.
  testImplementation(libs.json)
  androidTestImplementation(libs.androidx.junit)
}

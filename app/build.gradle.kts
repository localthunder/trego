 plugins {
            id("com.android.application")
            id("org.jetbrains.kotlin.android")
            id("org.jetbrains.kotlin.kapt")
            id("com.google.devtools.ksp")
            id("com.google.gms.google-services")
            id("org.jetbrains.kotlin.plugin.compose")

 }

android {
    namespace = "com.helgolabs.trego"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.helgolabs.trego"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
//    composeOptions {
//        kotlinCompilerExtensionVersion = "1.5.11"
//    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {

    implementation("com.google.android.material:material:1.12.0")
    implementation("com.materialkolor:material-color-utilities:2.1.1")
    implementation("com.materialkolor:material-kolor:2.1.1")
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    testImplementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-runtime-ktx:2.7.7")
    implementation("androidx.arch.core:core-common:2.2.0")
    implementation("androidx.arch.core:core-runtime:2.2.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("junit:junit:4.13.2")
    implementation("androidx.work:work-testing:2.9.1")
    implementation("androidx.datastore:datastore-core-android:1.1.1")
    implementation ("androidx.datastore:datastore-preferences:1.1.1")
    implementation ("com.google.accompanist:accompanist-permissions:0.33.2-alpha")


    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.5.1")
    testImplementation("org.mockito:mockito-inline:4.5.1")
    testImplementation ("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("com.google.truth:truth:1.1.3")
    testImplementation("io.mockk:mockk:1.12.5")
    testImplementation ("androidx.arch.core:core-testing:2.2.0")
    testImplementation("app.cash.turbine:turbine:0.12.1") // For testing Flows

    // AndroidX Test
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation ("androidx.test:runner:1.5.2")

    // Robolectric for unit tests that require Android framework
    testImplementation("org.robolectric:robolectric:4.11.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.arch.core:core-testing:2.1.0")
    androidTestImplementation("androidx.test:core:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp for logging
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.0")

    // Jetpack Compose
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.runtime:runtime-livedata:1.6.8")


    // Jetpack Navigation Compose
    implementation("androidx.navigation:navigation-compose")

    // Coroutines for async operations
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation ("com.jakewharton.retrofit:retrofit2-kotlin-coroutines-adapter:0.9.2")


    //JWT Token security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coil image loading
    implementation ("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")

    //Icons
    implementation ("androidx.compose.material:material-icons-extended")

    //Palette for gradient borders
    implementation ("androidx.palette:palette-ktx:1.0.0")

    //Biometrics
    implementation ("androidx.biometric:biometric:1.2.0-alpha05")

    // Room dependencies
    implementation ("androidx.room:room-runtime:2.6.1")
    ksp ("androidx.room:room-compiler:2.6.1")
    implementation ("androidx.room:room-ktx:2.6.1")

    //View Model
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose")

    //Play store reviews
    implementation("com.google.android.play:review-ktx:2.0.2")
    implementation("com.google.android.gms:play-services-measurement-api:22.2.0")  // Downgrade from 22.2.0
    implementation("com.google.android.gms:play-services-measurement-impl:22.2.0")

    //Firebase notifications
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics")

    //In app updates
    implementation ("com.google.android.play:app-update:2.1.0")
    implementation ("com.google.android.play:app-update-ktx:2.1.0")

    //Pull to refresh
    implementation("com.google.accompanist:accompanist-swiperefresh:0.36.0")

}
plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    compileSdk = MakeConfig.appCompileSDK
    buildToolsVersion = MakeConfig.appBuildTools

    defaultConfig {
        minSdk = MakeConfig.appMinSDK
        targetSdk = MakeConfig.appTargetSDK
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        encoding = "utf-8"
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${MakeConfig.kotlin_version}")
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:${MakeConfig.kotlin_version}")
    implementation("org.jetbrains.kotlin:kotlin-android-extensions-runtime:${MakeConfig.kotlin_version}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${MakeConfig.kotlin_coroutines}")
    implementation("com.squareup.okio:okio:${MakeConfig.okioVersion}")
    implementation("com.squareup.okhttp3:okhttp:${MakeConfig.okhttpLibraryVersion}")
    implementation("com.squareup.okhttp3:logging-interceptor:${MakeConfig.okhttpLibraryVersion}")
    implementation("io.reactivex.rxjava3:rxjava:${MakeConfig.rxJavaVersion}")
    implementation("io.reactivex.rxjava3:rxandroid:${MakeConfig.rxAndroidVersion}")
    implementation("androidx.annotation:annotation:${MakeConfig.annotationVersion}")
    implementation(project("path" to ":gson"))
}

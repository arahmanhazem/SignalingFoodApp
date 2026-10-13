import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

val aws = Properties().apply {
    val file = rootProject.file("aws.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\")
    .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

android {
    namespace = "com.abdelrahmanhazem.seller"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.abdelrahmanhazem.seller"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "2.0"
        listOf("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN", "AWS_REGION", "AWS_BUCKET").forEach { key ->
            buildConfigField("String", key, quoted(aws.getProperty(key, "")))
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // This course demo embeds team credentials. Do not distribute a release APK.
    buildTypes { release { isMinifyEnabled = false } }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.amazonaws:aws-android-sdk-s3:2.81.1")
}

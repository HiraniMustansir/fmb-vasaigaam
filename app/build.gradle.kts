import java.util.Properties
plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.plugin.serialization")
 id("com.google.devtools.ksp")
}
val config = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun setting(name: String, fallback: String) = System.getenv(name) ?: config.getProperty(name, fallback)
android {
 namespace = "org.vasaigaam.fmb"
 compileSdk = 36
 defaultConfig {
  applicationId = "org.vasaigaam.fmb"; minSdk = 26; targetSdk = 36
  versionCode = 2; versionName = "0.2.0"
  buildConfigField("String", "SUPABASE_URL", "\"${setting("SUPABASE_URL", "https://bdeqckqvvbllagezkugv.supabase.co")}\"")
  buildConfigField("String", "SUPABASE_KEY", "\"${setting("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_PUg7UQoCg5UHJluiBGn9Bw_x8g6-eL4")}\"")
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildTypes { release { isMinifyEnabled = false } }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.08.00"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
 implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
 implementation("io.github.jan-tennert.supabase:auth-kt")
 implementation("io.github.jan-tennert.supabase:postgrest-kt")
 implementation("io.github.jan-tennert.supabase:realtime-kt")
 implementation("io.github.jan-tennert.supabase:storage-kt")
 implementation("io.ktor:ktor-client-okhttp:3.1.3")
 implementation("io.insert-koin:koin-android:4.0.4")
 implementation("androidx.room:room-runtime:2.7.1")
 implementation("androidx.room:room-ktx:2.7.1")
 ksp("androidx.room:room-compiler:2.7.1")
 implementation("androidx.work:work-runtime-ktx:2.10.1")
 testImplementation("junit:junit:4.13.2")
}

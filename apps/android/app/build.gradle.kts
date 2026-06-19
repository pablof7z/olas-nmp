import org.gradle.internal.os.OperatingSystem

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.f7z.olas"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.f7z.olas"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin", "../Components")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    // lifecycle-runtime-compose and lifecycle-viewmodel-compose are NOT in the
    // compose-bom (they are in the lifecycle BOM separately). Pin to 2.8.2 to
    // match what was already used in the original build.gradle.kts.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.google.flatbuffers:flatbuffers-java:25.2.10")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")
    testImplementation("junit:junit:4.13.2")
}

// Cross-compile the JNI shim. Output lands in jniLibs for both shipped ABIs.
// rootProject.projectDir = apps/android/ → .parentFile.parentFile = repo root.
val cargoNdk by tasks.registering(Exec::class) {
    workingDir = rootProject.projectDir.parentFile.parentFile // repo root
    val cargo = "${System.getProperty("user.home")}/.cargo/bin/cargo"
    val bin = if (OperatingSystem.current().isWindows) "$cargo.exe" else cargo
    commandLine(
        bin, "ndk",
        "--manifest-path", "apps/olas/nmp-app-olas/Cargo.toml",
        "-t", "arm64-v8a", "-t", "x86_64",
        "-o", "apps/android/app/src/main/jniLibs",
        "build", "--release",
    )
}

tasks.named("preBuild") { dependsOn(cargoNdk) }

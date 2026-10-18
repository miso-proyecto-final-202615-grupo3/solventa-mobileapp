plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Backend que usa la app. Se cambia sin tocar código, p. ej. en ~/.gradle/gradle.properties:
//   solventa.useMockBackend=false
//   solventa.bffMobileUrl=http://10.0.2.2:8002   (debug: BFF Móvil en docker compose, visto desde el emulador)
//   solventa.release.bffMobileUrl=https://<api-id>.execute-api.us-east-1.amazonaws.com   (release: API Gateway)
// Debug usa el backend simulado salvo que se indique lo contrario; release usa el real salvo que se pida el simulado.
val useMockBackendProperty = providers.gradleProperty("solventa.useMockBackend").map { it.toBoolean() }
val useMockBackendDebug = useMockBackendProperty.orElse(true).get()
val useMockBackendRelease = useMockBackendProperty.orElse(false).get()
val bffMobileUrl = providers.gradleProperty("solventa.bffMobileUrl").orElse("http://10.0.2.2:8002").get()
val releaseBffMobileUrl = providers.gradleProperty("solventa.release.bffMobileUrl").orElse("").get().trim()

android {
    namespace = "com.solventa.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.solventa.app"
        minSdk = 26
        // Plataforma objetivo definida en la Visión de Arquitectura: Android API 34.
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "WEB_SIGNUP_URL", "\"https://solventa.example/registro\"")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_MOCK_BACKEND", useMockBackendDebug.toString())
            buildConfigField("String", "BFF_MOBILE_URL", "\"$bffMobileUrl\"")
        }
        release {
            buildConfigField("boolean", "USE_MOCK_BACKEND", useMockBackendRelease.toString())
            buildConfigField("String", "BFF_MOBILE_URL", "\"$releaseBffMobileUrl\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

// Un APK o bundle de release apuntando a HTTP o a un backend local no funcionaría (release exige HTTPS):
// se falla al empaquetar, no al configurar, para que lint y las pruebas de release sigan corriendo sin la URL.
tasks.matching { it.name == "packageRelease" || it.name == "bundleRelease" }.configureEach {
    val mock = useMockBackendRelease
    val url = releaseBffMobileUrl
    doFirst {
        check(mock || url.startsWith("https://")) {
            "El build release necesita -Psolventa.release.bffMobileUrl=https://... (URL del API Gateway) " +
                "o -Psolventa.useMockBackend=true para el APK de demostración. Valor recibido: '$url'."
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

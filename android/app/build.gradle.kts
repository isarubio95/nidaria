import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Servidor al que se conecta la app recién instalada. Se cambia en
// local.properties (nidaria.server=http://192.168.1.20:3000) o con
// -Pnidaria.server=…; dentro de la app también se puede cambiar.
// 10.0.2.2 es, visto desde el emulador, el ordenador que lo ejecuta.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val defaultServer: String = providers.gradleProperty("nidaria.server").orNull
    ?: localProperties.getProperty("nidaria.server")
    ?: "http://10.0.2.2:3000"

// Servidor de la versión de release. Para la tienda tiene que ser el
// servidor público con HTTPS (nidaria.releaseServer=https://…). Si no se
// indica, la release usa el mismo que la de depuración.
val releaseServer: String = providers.gradleProperty("nidaria.releaseServer").orNull
    ?: localProperties.getProperty("nidaria.releaseServer")
    ?: defaultServer

// Clave de subida a Google Play. Se lee de keystore.properties (junto a
// local.properties, fuera de git) o de variables de entorno, para poder
// firmar también en CI. Sin ella, la release se firma con la clave de
// depuración: vale para instalarla en un móvil propio, no para la tienda.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)
val uploadStoreFile = signingValue("storeFile", "NIDARIA_KEYSTORE")?.let { rootProject.file(it) }
val hasUploadKey = uploadStoreFile?.exists() == true

android {
    namespace = "com.nidaria.app"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.nidaria.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "DEFAULT_SERVER", "\"$defaultServer\"")
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = uploadStoreFile
                storePassword = signingValue("storePassword", "NIDARIA_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "NIDARIA_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "NIDARIA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "DEFAULT_SERVER", "\"$releaseServer\"")
            signingConfig = signingConfigs.getByName(if (hasUploadKey) "upload" else "debug")
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
}

// El AAB solo sirve para subirlo a Google Play: no se genera si va a
// salir firmado con la clave de depuración o apuntando a un servidor sin
// HTTPS, que es justo lo que la tienda (o sus jugadores) no aceptan. Se
// comprueba antes de ejecutar ninguna tarea, para que no llegue a quedar
// un AAB mal firmado en build/outputs.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.path == ":app:bundleRelease" }) {
        check(hasUploadKey) {
            "Falta la clave de subida: crea android/keystore.properties (ver android/README.md)."
        }
        check(releaseServer.startsWith("https://")) {
            "nidaria.releaseServer tiene que ser una dirección https:// (ahora: $releaseServer)."
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

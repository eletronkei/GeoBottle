plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services") // Google Services Plugin
}

android {
    namespace = "com.felicio.geobottle"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.felicio.geobottle"
        minSdk = 23
        targetSdk = 34
        versionCode = 22
        versionName = "2.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // Cria o signingConfig chamado "release"
        create("release") {
            storeFile = file("C:/chaveaab/geobottle/geobottle_keystore.jks") // Define o caminho do keystore
            storePassword = "AAs06t10" // Senha do keystore
            keyAlias = "key0" // Alias do keystore
            keyPassword = "AAs06t10" // Senha do alias
        }
    }

    buildTypes {
        getByName("release") {
            // Configuração de assinatura usando o signingConfig
            signingConfig = signingConfigs.getByName("release")

            // Ativa a ofuscação e redução de código no modo release
            isMinifyEnabled = true

            // Ativa a remoção de recursos não utilizados no Kotlin DSL
            isShrinkResources = true

            // Gera símbolos de depuração para código nativo (C/C++) no modo release
            ndk {
                // Gera um nível intermediário de símbolos de depuração para reduzir o tamanho
                debugSymbolLevel = "SYMBOL_TABLE"
            }

            // Definição dos arquivos de Proguard
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))
        }
        getByName("debug") {
            isMinifyEnabled = false

            // Gera símbolos completos de depuração no modo debug
            ndk {
                debugSymbolLevel = "FULL"  // Gera informações completas para depuração mais detalhada
            }
        }
    }


    buildFeatures {
        viewBinding = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}


dependencies {
    // Core e UI
    implementation(libs.androidx.core.ktx.v1101)
    implementation(libs.androidx.appcompat.v161)
    implementation(libs.material.v180)
    implementation(libs.androidx.constraintlayout)

    // Jetpack Compose
    implementation(libs.androidx.activity.compose.v172)
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx.v262)
    implementation(libs.androidx.runtime.livedata)

    // Google Play Billing
    implementation(libs.billing.ktx.v601)

    // Firebase and Google Play Services
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.play.services.auth) // Certifier-se de adicionar esta linha
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // Navigation components
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Lifecycle components
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v115)
    androidTestImplementation(libs.androidx.espresso.core.v351)

    // Testes e debugging para Compose
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}

// Aplique o plugin do Google Services
apply(plugin = "com.google.gms.google-services")

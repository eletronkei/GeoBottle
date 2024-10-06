buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.6.1") // Gradle Plugin para Android
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0") // Kotlin Plugin
        classpath("com.google.gms:google-services:4.3.15") // Google Services Plugin

    }
}

plugins {
    // Gradle e Kotlin Plugins
    id("com.android.application") version "8.6.1" apply false
    id("com.android.library") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false

    // Google Services Plugin
    id("com.google.gms.google-services") version "4.3.15" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}





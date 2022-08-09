
buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
    }
}


plugins {
    base
    kotlin("jvm") version "1.7.10" apply false
//    kotlin("plugin.serialization") version "1.6.10" apply false
}

allprojects {
    group = "org.ta4j"
    version = "0.15-SNAPSHOT"

    repositories {
        mavenCentral()
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "11"
        }
    }

}
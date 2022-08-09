import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  //  kotlin("jvm") version "1.7.10"
    java
}

group = "org.ta4j"
version = "0.15-SNAPSHOT"


repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":ta4j-core"))
  //  testImplementation(kotlin("test"))
 //   testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("junit:junit:4.13.2")

    implementation("ch.qos.logback:logback-classic:1.2.11")
    testImplementation("org.apache.poi:poi:5.2.2")
    testImplementation("org.apache.commons:commons-math3:3.6.1")
    implementation(group= "org.jfree", name= "jfreechart", version= "1.5.0")
    implementation(group= "org.jfree", name= "jcommon", version= "1.0.24")
    implementation("com.opencsv:opencsv:3.9") // 5.6
    implementation("com.google.code.gson:gson:2.8.6")


}

tasks.test {
   // useJUnitPlatform()
}

//tasks.withType<KotlinCompile> {
//    kotlinOptions {
//        jvmTarget = "1.8"
//        freeCompilerArgs = listOf("-Xjvm-default=all")
//    }
//}
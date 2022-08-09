plugins {
    id("java")
}

group = "org.ta4j"
version = "0.15-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":ta4j-core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("ch.qos.logback:logback-classic:1.2.11")
    testImplementation("org.apache.poi:poi:5.2.2")
    testImplementation("org.apache.commons:commons-math3:3.6.1")



//    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
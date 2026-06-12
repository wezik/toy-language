plugins {
    kotlin("jvm") version "2.2.21"
    application
}

application {
    mainClass = "dev.wezik.toy.MainKt"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in` // keeps the REPL usable via 'gradlew run'
}

group = "dev.wezik"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

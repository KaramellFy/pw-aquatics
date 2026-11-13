plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.pwaquatics"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val mapstructVersion = "1.6.3"

dependencies {
    // Web, security, persistence
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    // Argon2id password hashing (used by Spring Security's Argon2PasswordEncoder)
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // API documentation (disabled in production via configuration)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")

    // Infrastructure
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.22.0")
    implementation("net.javacrumbs.shedlock:shedlock-spring:7.11.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.11.0")
    implementation("com.fasterxml.uuid:java-uuid-generator:5.2.0")
    implementation("org.commonmark:commonmark:0.32.0")
    // Product photos: resizing and EXIF orientation (pure Java) and WebP encoding (bundled libwebp)
    implementation("net.coobird:thumbnailator:0.4.21")
    implementation("com.github.usefulness:webp-imageio:0.11.0")
    // Card payments: official Stripe SDK (API calls, webhook signature check); see ADR-010
    implementation("com.stripe:stripe-java:34.1.0")
    // Its model classes are annotated for Gson (a runtime dependency); this only keeps javac from warning about it.
    compileOnly("com.google.code.gson:gson")

    // Mapping
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial", "-Amapstruct.defaultComponentModel=spring"))
}

tasks.test {
    useJUnitPlatform()
    // webp-imageio loads its bundled libwebp through JNI.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // Testcontainers needs Docker. Set TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX to use a registry mirror if needed.
    systemProperty("user.timezone", "UTC")
}

tasks.bootRun {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.bootJar {
    archiveFileName = "app.jar"
}

tasks.jar {
    enabled = false
}

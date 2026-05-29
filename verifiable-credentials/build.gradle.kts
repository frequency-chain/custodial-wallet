plugins {
    kotlin("jvm")
    id("io.spring.dependency-management")
    id("custodial-wallet.jvm-common")
    id("custodial-wallet.jvm-default-quick-test")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    implementation("commons-codec:commons-codec")
    implementation("com.google.crypto.tink:tink")

    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito.kotlin:mockito-kotlin")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
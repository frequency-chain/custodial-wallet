plugins {
  kotlin("jvm")
  id("io.spring.dependency-management")
  id("custodial-wallet.jvm-common")
  id("custodial-wallet.jvm-default-quick-test")
}

dependencies {
  implementation(project(":clients"))
  implementation(project(":common"))
  implementation(project(":db"))
  implementation(project(":util"))
  implementation(project(":verifiable-credentials"))

  implementation("com.google.guava:guava")
  implementation("com.strategyobject.substrateclient:crypto")
  implementation("io.amplica.frequency:saas-frequency-client")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")
  implementation("org.slf4j:slf4j-api")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("org.springframework.security:spring-security-crypto")
  implementation("com.google.crypto.tink:tink")
  implementation("org.bouncycastle:bcprov-jdk18on")
  implementation("com.goterl:lazysodium-java")
  implementation("com.codahale:aes-gcm-siv")
  implementation("io.projectliberty.icssdk:lib")
  implementation("io.arrow-kt:arrow-core")

  testImplementation("org.assertj:assertj-core")
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.mockito.kotlin:mockito-kotlin")

  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
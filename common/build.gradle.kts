plugins {
  kotlin("jvm")
  id("io.spring.dependency-management")
  id("custodial-wallet.jvm-common")
  id("custodial-wallet.jvm-default-quick-test")
}

dependencies {
  compileOnly("org.springframework.boot:spring-boot-configuration-processor")

  implementation("com.fasterxml.jackson.core:jackson-annotations")
  implementation("com.github.spullara.mustache.java:compiler")
  implementation("com.google.guava:guava")
  implementation("com.googlecode.libphonenumber:libphonenumber")
  implementation("io.netty:netty-codec-http")
  implementation("net.logstash.logback:logstash-logback-encoder")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")
  implementation("org.slf4j:slf4j-api")
  implementation("org.springframework:spring-web")
  implementation("org.springframework.boot:spring-boot-starter")

  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("org.junit.jupiter:junit-jupiter")

  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
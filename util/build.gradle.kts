plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("io.spring.dependency-management")
  id("custodial-wallet.jvm-common")
  id("custodial-wallet.jvm-default-quick-test")
}

dependencies {
  kapt("com.strategyobject.substrateclient:scale-codegen")
  kapt("com.strategyobject.substrateclient:rpc-codegen")
  kapt("com.strategyobject.substrateclient:pallet-codegen")
  implementation("com.strategyobject.substrateclient:common")
  implementation("com.strategyobject.substrateclient:crypto")
  implementation("com.strategyobject.substrateclient:scale")
  implementation("com.strategyobject.substrateclient:transport")
  implementation("com.strategyobject.substrateclient:rpc")
  implementation("com.strategyobject.substrateclient:rpc-api")
  implementation("com.strategyobject.substrateclient:pallet")
  implementation("com.strategyobject.substrateclient:api")
  implementation("com.google.guava:guava")
  implementation("commons-codec:commons-codec")
  implementation("com.fasterxml.jackson.core:jackson-annotations")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("io.amplica.frequency:saas-frequency-client")
  implementation("io.arrow-kt:arrow-core")
  implementation("org.web3j:core")
  implementation("net.logstash.logback:logstash-logback-encoder")
  implementation("org.slf4j:slf4j-api")

  runtimeOnly("ch.qos.logback.contrib:logback-jackson")
  runtimeOnly("ch.qos.logback.contrib:logback-json-classic")

  testImplementation("org.assertj:assertj-core")
  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.mockito.kotlin:mockito-kotlin")

  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

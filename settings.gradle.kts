rootProject.name = "custodial-wallet"

include(
  "app",
  "clients",
  "common",
  "db",
  "email",
  "frontend",
  "service",
  "util",
  "verifiable-credentials",
  "web"
)

pluginManagement {
  val springBootVersion: String by settings // Pulls from `gradle.properties`

  // Pin plugin versions for all submodules
  plugins {
    id("org.springframework.boot") version springBootVersion
    id("io.spring.dependency-management") version "1.1.4"
    id("io.miret.etienne.sass") version "1.4.2" apply false
    id("com.github.node-gradle.node") version "7.0.2"
    id("de.richsource.gradle.plugins.typescript") version "1.8.0"
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    kotlin("kapt") version "1.9.22"
  }
}

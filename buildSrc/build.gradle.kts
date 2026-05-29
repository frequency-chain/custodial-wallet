plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation("io.spring.gradle:dependency-management-plugin:1.1.4")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
  implementation("com.github.node-gradle:gradle-node-plugin:7.0.2")
}

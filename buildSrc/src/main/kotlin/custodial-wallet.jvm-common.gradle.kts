// Build logic shared by all the JVM-targeting submodules

plugins {
    jacoco
}

// Sourced from gradle.properties
val springBootVersion: String by project
val testcontainersVersion: String by project
val linuxSubstrateClientVersion: String by project
val nonLinuxSubstrateClientVersion: String by project
val frequencyClientVersion: String by project
val springdocVersion: String by project
val graphSdkVersion: String by project
val playwrightVersion: String by project
val logstashLogbackEncoderVersion: String by project

val substrateClientVersion = if (System.getProperty("os.name").lowercase() == "linux") {
  linuxSubstrateClientVersion
} else {
  nonLinuxSubstrateClientVersion
}

val parallelTestsProperty = project.findProperty("io.amplica.custodial_wallet.parallel_tests.enabled") as String?
val parallelTestsEnabled = parallelTestsProperty?.toBooleanStrict() == true

group = "io.amplica.custodial_wallet"
version = "0.0.1-SNAPSHOT"

repositories {
  mavenLocal()
  mavenCentral()
  maven("https://jitpack.io")

  maven {
    name = "Saas Frequency Client on GitHub Packages"
    url = uri("https://maven.pkg.github.com/ProjectLibertyLabs/saas-frequency-client")
    credentials {
      username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
      password = findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
    }
  }

  maven {
    name = "SubstrateClientJava"
    url = uri("https://maven.pkg.github.com/ProjectLibertyLabs/substrate-client-java")
    credentials {
      username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
      password = findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
    }
  }

  maven {
    name = "GithubPackages"
    url = uri("https://maven.pkg.github.com/ProjectLibertyLabs/graph-sdk")
    credentials {
      username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
      password = findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
    }
  }

  maven {
    name = "ICS SDK Github Packages"
    url = uri("https://maven.pkg.github.com/ProjectLibertyLabs/ics-sdk")
    credentials {
      username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
      password = findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
    }
  }
}

extensions.configure<JavaPluginExtension> {
  toolchain {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
  imports {
    mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
    mavenBom("org.springframework.boot:spring-boot-starter-parent:$springBootVersion") {
      // Use latest version of Junit5 to get access to parameterized suites
      bomProperty("junit-jupiter.version", "5.13.3")
      // Use latest version of testcontainers to meet the current docker minimum API version
      bomProperty("testcontainers.version", testcontainersVersion)
    }
    mavenBom("software.amazon.awssdk:bom:2.34.2")
  }
  dependencies {
    dependency("org.springframework.boot:spring-boot-starter-data-redis:${property("springBootVersion")}")
    dependency("org.springframework.boot:spring-boot-devtools:${property("springBootVersion")}")

    dependency("com.google.guava:guava:31.1-jre")
    dependency("org.springdoc:springdoc-openapi-starter-webflux-ui:$springdocVersion")
    dependency("org.springdoc:springdoc-openapi-starter-common:$springdocVersion")
    dependency("ch.qos.logback.contrib:logback-jackson:0.1.5")
    dependency("ch.qos.logback.contrib:logback-json-classic:0.1.5")
    dependency("org.mockito.kotlin:mockito-kotlin:5.4.0")
    dependency("org.flywaydb:flyway-core:8.5.11")
    dependency("org.postgresql:postgresql:42.3.6")
    dependency("org.postgresql:r2dbc-postgresql:1.1.1.RELEASE")
    dependency("org.json:json:20220320")
    dependency("io.amplica.frequency:saas-frequency-client:$frequencyClientVersion")
    dependency("io.amplica.graphsdk:lib:$graphSdkVersion")
    dependency("com.webauthn4j:webauthn4j-core:0.24.1.RELEASE")
    dependency("com.google.crypto.tink:tink:1.15.0")
    dependency("com.codahale:aes-gcm-siv:0.4.3")

    dependency("com.strategyobject.substrateclient:common:$substrateClientVersion")
    dependency("com.strategyobject.substrateclient:crypto:$substrateClientVersion")
    dependency("com.strategyobject.substrateclient:scale:$substrateClientVersion")
    dependency("com.strategyobject.substrateclient:transport:$substrateClientVersion")
    dependency("com.strategyobject.substrateclient:rpc:$substrateClientVersion")
    dependency("com.strategyobject.substrateclient:rpc-api:$substrateClientVersion")
    dependency("com.strategyobject.substrateclient:pallet:$substrateClientVersion")
    dependency("com.strategyobject.substrateclient:api:$substrateClientVersion")
    dependency("com.strategyobject.substrateclient:scale-codegen:$substrateClientVersion")
    dependency("com.strategyobject.substrateclient:rpc-codegen:$substrateClientVersion")
    dependency("com.strategyobject.substrateclient:pallet-codegen:$substrateClientVersion")

    dependency("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.6.4")
    dependency("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
    dependency("io.arrow-kt:arrow-core:1.1.3")
    dependency("io.sentry:sentry-spring-boot-starter-jakarta:6.15.0")
    dependency("io.sentry:sentry-logback:6.15.0")
    dependency("io.micrometer:micrometer-registry-prometheus:1.15.4")
    dependency("com.twilio.sdk:twilio:9.6.2")
    dependency("com.github.spullara.mustache.java:compiler:0.9.10")
    dependency("com.googlecode.libphonenumber:libphonenumber:8.13.17")
    dependency("com.microsoft.playwright:playwright:$playwrightVersion")
    dependency("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    dependency("org.wiremock:wiremock-standalone:3.9.1")
    dependency("org.web3j:core:4.14.0")
    dependency("org.bouncycastle:bcprov-jdk18on:1.82")
    dependency("com.goterl:lazysodium-java:5.2.0")
    dependency("io.projectliberty.icssdk:lib:0.0.4")
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    jvmTarget = "21"
    freeCompilerArgs = listOf("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn")
    javaParameters =
      true //https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x#parameter-name-retention
  }
}

tasks.withType<Test>().configureEach {
  // https://docs.gradle.org/current/userguide/performance.html#a_run_tests_in_parallel
  if (parallelTestsEnabled) {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
  }

  useJUnitPlatform()
  testLogging {
    events("passed", "skipped", "failed")
    /*
    FYI everything other than showStandardStreams are pretty useless in terms of troubleshooting the output of the CLI
    if they do anything I find it difficult to tell for our application
      */
    showCauses = true
    showStackTraces = true
    showExceptions = true
    showStandardStreams = false
  }
  //finalizedBy("jacocoTestReport") // report is always generated after tests run
}

tasks.withType<JacocoReport>().configureEach {
    dependsOn(tasks.withType<Test>())
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<Jar> {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
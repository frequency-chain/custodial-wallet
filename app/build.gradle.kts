import io.miret.etienne.gradle.sass.CompileSass
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
  id("org.springframework.boot")
  id("io.miret.etienne.sass")
  id("io.spring.dependency-management")
  kotlin("jvm")
  kotlin("plugin.spring")
  kotlin("kapt")
  id("java-test-fixtures")
  id("custodial-wallet.jvm-common")
  id("jacoco-report-aggregation")
}

val frontendJs by configurations.creating
val frontendCss by configurations.creating
val webBundles by configurations.creating

dependencies {
  implementation(project(":clients"))
  implementation(project(":common"))
  implementation(project(":db"))
  implementation(project(":email"))
  implementation(project(":service"))
  implementation(project(":util"))
  implementation(project(":verifiable-credentials"))

  // Artifacts from peer projects
  frontendJs(project(path = ":frontend", configuration = "jsBundle"))
  frontendCss(project(path = ":frontend", configuration = "css"))
  webBundles(project(path = ":web", configuration = "jsBundles"))

  kapt("com.strategyobject.substrateclient:scale-codegen")
  kapt("com.strategyobject.substrateclient:rpc-codegen")
  kapt("com.strategyobject.substrateclient:pallet-codegen")

  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("com.google.guava:guava")
  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui")
  implementation("org.springdoc:springdoc-openapi-starter-common")
  implementation("io.amplica.frequency:saas-frequency-client")
  implementation("com.strategyobject.substrateclient:common")
  implementation("com.strategyobject.substrateclient:crypto")
  implementation("com.strategyobject.substrateclient:scale")
  implementation("com.strategyobject.substrateclient:transport")
  implementation("com.strategyobject.substrateclient:rpc")
  implementation("com.strategyobject.substrateclient:rpc-api")
  implementation("com.strategyobject.substrateclient:pallet")
  implementation("com.strategyobject.substrateclient:api")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("io.arrow-kt:arrow-core")
  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("io.sentry:sentry-spring-boot-starter-jakarta")
  implementation("io.sentry:sentry-logback")
  implementation("com.github.spullara.mustache.java:compiler")
  implementation("com.googlecode.libphonenumber:libphonenumber")
  implementation("software.amazon.awssdk:ses")
  implementation("com.twilio.sdk:twilio")
  implementation("net.logstash.logback:logstash-logback-encoder")
  implementation("com.webauthn4j:webauthn4j-core")
  implementation("io.projectliberty.icssdk:lib")
  implementation("org.bouncycastle:bcprov-jdk18on")

  runtimeOnly("io.micrometer:micrometer-registry-prometheus")
  runtimeOnly("ch.qos.logback.contrib:logback-jackson")
  runtimeOnly("ch.qos.logback.contrib:logback-json-classic")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("org.testcontainers:postgresql")
  testImplementation("org.testcontainers:localstack")
  testImplementation("org.mockito.kotlin:mockito-kotlin")
  testImplementation("software.amazon.awssdk:kms")
  testImplementation("io.netty:netty-codec-http")
  testImplementation("com.microsoft.playwright:playwright")
  testImplementation("commons-codec:commons-codec:1.16.1")
  testImplementation("org.wiremock:wiremock-standalone")
  testImplementation("org.awaitility:awaitility-kotlin")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  developmentOnly("org.springframework.boot:spring-boot-devtools")

  jacocoAggregation(project(":clients"))
  jacocoAggregation(project(":common"))
  jacocoAggregation(project(":db"))
  jacocoAggregation(project(":email"))
  jacocoAggregation(project(":service"))
  jacocoAggregation(project(":util"))
  jacocoAggregation(project(":verifiable-credentials"))
}

tasks.named<Delete>("clean") {
  // NOTE(Julian, 2025-07-09): Added to ensure deprecated files get removed when developers build locally.
  // Can be removed once the `web` project PR has been merged and all developers are able to build main.
  delete(
    layout.buildDirectory,
    layout.projectDirectory.dir("node_modules"),
    layout.projectDirectory.dir("generated"),
    layout.projectDirectory.dir("src/main/resources/static/js/bundle/"),
  )
}

tasks.named<BootRun>("bootRun") {
  classpath = sourceSets["test"].runtimeClasspath
  classpath(configurations["developmentOnly"])
  args("--spring.profiles.active=dev,testui,enableTesting")
}

tasks.named<CompileSass>("compileSass") {
  setSourceDir(project.file("${projectDir}/src/main/resources/static/saas"))
  outputDir = project.file("${projectDir}/build/resources/main/static/css")
}

tasks.register<JavaExec>("installPlaywright") {
  classpath(sourceSets["test"].runtimeClasspath)
  mainClass.set("com.microsoft.playwright.CLI")
  args = listOf("install", "--with-deps")
}

tasks.register<Test>("quickTest") {
  group = "verification"

  // Configure our playwright extension to test against an abbreviated set of browsers
  environment("ENABLE_BROWSER_SKIP", 1)

  useJUnitPlatform {
    excludeTags(
      "Deprecated", // Skip tests for deprecated features (e.g., Webviews)
      // TODO: Mark the password-related tests as shelved once Svelte transition is completed
      "Shelved", // Skip testing functionality that is not actively being developed (e.g., OAuth)
      "Slow", // Skip tests that cost us more in execution time than they are worth
    )
  }
}

tasks.processResources {
  dependsOn(
    "compileSass",
    frontendJs,
    frontendCss,
    webBundles
  )

  inputs.dir("src/main/resources/")

  from(frontendJs) { into("static/js") }
  from(frontendCss) { into("static/css") }
  from(webBundles) { into("static/js/bundle") }

  filesMatching("**/*.html") {
    filter {
      //This is admittedly stupid, but you can't change the placeholder text in Gradle and it happens to use the most
      //popular string interpolation pattern in the entire JVM ecosystem. So now I need to go in explicitly looking
      //for what I want to replace, blegh
      it.replace("\${BUILD_NUMBER}", System.getenv().getOrDefault("CIRCLE_BUILD_NUM", "local"))
    }
  }
}

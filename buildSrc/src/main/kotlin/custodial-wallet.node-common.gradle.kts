import com.github.gradle.node.npm.task.NpmTask

// NOTE: Using this plugin requires implementing a `npmBuild` task to define the build process

extensions.configure<com.github.gradle.node.NodeExtension> {
  // Whether to download and install a specific Node.js version or not
  // If false, it will use the globally installed Node.js
  // If true, it will download node using above parameters
  // Note that npm is bundled with Node.js
  download = true
  version = "22.14.0"
}

// ============================================================
// Configure standard tasks to match java projects
// ============================================================

tasks.register<Delete>("clean") {
  group = "build"
  delete(layout.buildDirectory, "node_modules", ".eslintcache")
}

tasks.register("build") {
  group = "build"

  dependsOn(
    "check",
    "assemble"
  )
}

tasks.register("check") {
  group = "verification"

  // Runs all 'checks' and tests
  dependsOn(
    "typescriptCheck",
    "test",
  )
}

tasks.register("typescriptCheck") {
  group = "verification"

  dependsOn(
    "npmLint",
    "npmFormat",
  )
}

tasks.register("test") {
  group = "verification"

  dependsOn(
    "npmTest"
  )
}

// Alias for `test` to allow calling `quickTest` in the project root
tasks.register("quickTest") {
  group = "verification"

  dependsOn(
    "test"
  )
}

tasks.register("assemble") {
  group = "build"

  dependsOn(
    "npmBuild"
  )
}

// ============================================================
// Define common npm tasks
// ============================================================

// Assumes the project is using typescript and `eslint` with `--cache` flag
tasks.register<NpmTask>("npmLint") {
  dependsOn(tasks.named("npmInstall"))

  inputs.dir("src")
  inputs.file("eslint.config.mjs")

  outputs.file(".eslintcache")

  npmCommand.set(listOf("run", "lint"))
}

// Assumes the project is using typescript and `prettier` with `--cache` flag
tasks.register<NpmTask>("npmFormat") {
  dependsOn(tasks.named("npmInstall"))

  inputs.dir("src")
  inputs.file(".prettierrc")

  outputs.file("node_modules/.cache/prettier/.prettier-cache")

  npmCommand.set(listOf("run", "format"))
}

// Assumes the project is using typescript and `prettier`
tasks.register<NpmTask>("npmFormatFix") {
  dependsOn(tasks.named("npmInstall"))

  inputs.dir("src")
  inputs.file(".prettierrc")

  npmCommand.set(listOf("run", "format:fix"))
}

// Assumes the project is using typescript and `vitest`
tasks.register<NpmTask>("npmTest") {
  dependsOn(tasks.named("npmInstall"))

  inputs.dir("src")
  inputs.file("tsconfig.json")
  inputs.file("vite.config.ts")

  outputs.dir("node_modules/.vite/vitest")

  npmCommand.set(listOf("test"))
}

import com.github.gradle.node.npm.task.NpmTask

plugins {
  id("com.github.node-gradle.node")
  id("custodial-wallet.node-common")
}

configurations {
  create("jsBundle") {
    isCanBeConsumed = true
    isCanBeResolved = false
  }
  create("css") {
    isCanBeConsumed = true
    isCanBeResolved = false
  }
}

artifacts {
  add("jsBundle", layout.buildDirectory.file("frontend.js")) {
    builtBy("npmBuild")
  }
  add("css", layout.buildDirectory.file("frontend.css")) {
    builtBy("npmBuild")
  }
}

tasks.register<NpmTask>("npmBuild") {
  dependsOn(tasks.npmInstall)

  inputs.dir("src")
  inputs.file("tsconfig.json")
  inputs.file("vite.config.ts")

  outputs.dir(layout.buildDirectory)

  npmCommand.set(listOf("run", "build"))
}

tasks.register<NpmTask>("storybook") {
  dependsOn("npmBuild")

  inputs.dir("src")

  npmCommand.set(listOf("run", "storybook"))
}

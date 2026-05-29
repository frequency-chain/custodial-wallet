import com.github.gradle.node.npm.task.NpxTask

plugins {
  id("com.github.node-gradle.node")
  id("custodial-wallet.node-common")
}

configurations {
  create("jsBundles") {
    isCanBeConsumed = true
    isCanBeResolved = false
  }
}

artifacts {
  add("jsBundles", layout.buildDirectory.dir("bundles")) {
    builtBy("npmBuild")
    type = "directory"
  }
}

tasks.register("npmBuild") {
  group = "npm"

  dependsOn(
    "npmBundleChaintest",
    "npmBundleIFrame",
    "npmBundleRegistration",
    "npmBundlePasskeyTransaction",
    "npmBundleErrors",
    "npmBundleSentry"
  )
}

tasks.register<NpxTask>("npmCompileTs") {
  dependsOn(tasks.npmInstall)

  inputs.dir("src/main/ts")
  inputs.file("tsconfig.json")
  inputs.file("vite.config.ts")
  outputs.dir("${layout.buildDirectory.get()}/generated/src/main/js")

  command.set("tsc")
}

fun registerBundleTask(
  name: String,
  entrypoint: String,
  output: String,
  globalName: String? = null
) {
  tasks.register<NpxTask>(name) {
    val argsList = listOf(entrypoint, "--bundle", "--outfile=$output")
    val optionalArgs = if (globalName != null) listOf("--global-name=$globalName") else emptyList()

    dependsOn("npmCompileTs")

    inputs.dir("${layout.buildDirectory.get()}/generated/src/main/js")
    outputs.file(output)

    command.set("esbuild")
    args.set(argsList + optionalArgs)
  }
}

registerBundleTask(
  "npmBundleChaintest",
  "build/generated/src/main/js/chaintest.js",
  "build/bundles/chaintestBundle.js"
)

registerBundleTask(
  "npmBundleIFrame",
  "build/generated/src/main/js/passkey/iframe/iframeHandler.js",
  "build/bundles/iframeBundle.js",
  "PASSKEY_WALLET_IFRAME"
)

registerBundleTask(
  "npmBundleRegistration",
  "build/generated/src/main/js/passkey/iframe/iframeListener.js",
  "build/bundles/registrationBundle.js",
  "PASSKEY_WALLET"
)

registerBundleTask(
  "npmBundlePasskeyTransaction",
  "build/generated/src/main/js/passkey/transaction/transaction.js",
  "build/bundles/passkeyTransactionBundle.js",
  "PASSKEY_WALLET"
)

registerBundleTask(
  "npmBundleErrors",
  "build/generated/src/main/js/errors/index.js",
  "build/bundles/errorsBundle.js",
  "ERRORS"
)

registerBundleTask(
  "npmBundleSentry",
  "build/generated/src/main/js/sentry.js",
  "build/bundles/sentryBundle.js",
  "SENTRY"
)

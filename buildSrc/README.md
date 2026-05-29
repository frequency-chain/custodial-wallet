# BuildSrc

This directory is the idiomatic location in which to write custom gradle plugins. 
For more details on `buildSrc` see the [Gradle Docs](https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html). 

## Plugins

### `custodial-wallet.jvm-common`

This applies all the shared configuration (that used to live in `./build.gradle.kts`) to any project that uses it.

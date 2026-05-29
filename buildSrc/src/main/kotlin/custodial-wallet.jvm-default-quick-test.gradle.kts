// Default implementation of `quickTest` that delegates to `test`--for modules that don't
// have an abbreviated test suite--to allow calling `quickTest` in the project root.
//
// NOTE: Only intended for use in JVM gradle projects.
tasks.register<Test>("quickTest") {
  group = "verification"

  dependsOn("test")
}

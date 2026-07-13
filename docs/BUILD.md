# Build and Dependency Standards

MiniSearch uses the Maven Wrapper and the Spring Boot parent POM as the authoritative dependency and plugin-management baseline.

## Requirements

- Java 17
- Maven 3.9 or newer in the Maven 3 release line

Use the checked-in wrapper rather than a machine-specific Maven installation:

```shell
./mvnw verify
```

## Version-management policy

1. Dependencies managed by Spring Boot must omit an explicit version.
2. Libraries outside Spring Boot's dependency management must use either an imported BOM or a named property in `pom.xml`.
3. Maven plugins not managed by the Spring Boot parent must have a named version property.
4. Snapshot dependencies and dynamic version ranges are not allowed in a release build.
5. Framework version upgrades must be isolated changes with the full test suite and dependency-tree review.

The Maven Enforcer plugin rejects unsupported Java/Maven versions, duplicate dependency declarations, and dependency convergence conflicts during every build.

## Common commands

```shell
# Compile and run unit tests
./mvnw test

# Run all verification checks and create the executable JAR
./mvnw verify

# Inspect the selected dependency graph
./mvnw dependency:tree

# Inspect inherited dependency and plugin management
./mvnw help:effective-pom
```

Integration and performance test commands will be added when those test suites are introduced.

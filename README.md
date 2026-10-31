# Testcontainers CUBRID Module

[![CI](https://github.com/CUBRID/testcontainers-cubrid/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/CUBRID/testcontainers-cubrid/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.cubrid/testcontainers-cubrid.svg)](https://central.sonatype.com/artifact/org.cubrid/testcontainers-cubrid)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A [Testcontainers](https://testcontainers.com) module for running [CUBRID](https://www.cubrid.org) in JAVA integration tests.

## Installation

### Gradle

```groovy
dependencies {
    testImplementation 'org.cubrid:testcontainers-cubrid:0.1.0'
    testRuntimeOnly   'org.cubrid:cubrid-jdbc:11.3.2.0053'
}
```

### Maven

```xml
<dependency>
    <groupId>org.cubrid</groupId>
    <artifactId>testcontainers-cubrid</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.cubrid</groupId>
    <artifactId>cubrid-jdbc</artifactId>
    <version>11.3.2.0053</version>
    <scope>test</scope>
</dependency>
```

## Usage

### Programmatic

```java
import org.testcontainers.cubrid.CubridContainer;

try (CubridContainer cubrid = new CubridContainer("cubrid/cubrid:11.4")) {
    cubrid.start();

    try (Connection conn = DriverManager.getConnection(
            cubrid.getJdbcUrl(), cubrid.getUsername(), cubrid.getPassword());
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT 1")) {
        // ...
    }
}
```

### JDBC URL (automatic lifecycle)

Testcontainers starts and stops the container based on the URL:

```java
String url = "jdbc:tc:cubrid:11.4://localhost/mydb";
try (Connection conn = DriverManager.getConnection(url)) {
    // ...
}
```

## Configuration

| Method | Default | Description |
|---|---|---|
| `withDatabaseName(String)` | `testdb` | Database name to create on startup. |
| `withUsername(String)` | `testuser` | Application user. Cannot be `dba` or `public` (CUBRID reserved). |
| `withPassword(String)` | `testpass` | Password for the application user. May be empty; must not be `null`. |
| `withInitScript(String)` | — | Classpath resource SQL script run after the container is ready. |
| `withUrlParam(String, String)` | — | Additional JDBC URL parameter. |

The container exposes broker port `33000`. Use `getJdbcUrl()` for the mapped URL or `getMappedPort(33000)` for the port number.

## Compatibility

- Java 8+ (the module is published targeting Java 8 bytecode)
- Docker
- CUBRID image: `cubrid/cubrid`
- `withUsername()` does not accept `dba` or `public`. Those accounts already exist in CUBRID by default; pick any other name and the container will create that user with the password from `withPassword()`.

## Building

Requirements: JDK 17 and Docker.

```sh
./gradlew build
```

### Testing against other CUBRID versions

`./gradlew test` runs the tests against `cubrid/cubrid:11.4`. Pass `-Pcubrid.version` to run them against another image:

```sh
./gradlew test -Pcubrid.version=10.2
```

| `cubrid.version` | Image |
|---|---|
| `10.2` | `cubrid/cubrid:10.2` |
| `11.0` | `cubrid/cubrid:11.0` |
| `11.2` | `cubrid/cubrid:11.2` |
| `11.3` | `cubrid/cubrid:11.3` |
| `11.4` | `cubrid/cubrid:11.4` |
| `nightly` | `ghcr.io/srltas/cubrid-nightly:nightly` (nightly build of CUBRID `develop`) |

Each version also has its own task: `testCubrid10_2`, `testCubrid11_0`, `testCubrid11_2`, `testCubrid11_3`, `testCubrid11_4` and `testCubridNightly`. `testAllVersions` runs all six in parallel; add `--continue` to see every version's result even when one fails, or `--max-workers=2` to run fewer at once:

```sh
./gradlew testAllVersions --continue
```

The version tasks always run, because image tags, including the release ones, are republished in place. Docker still reuses an image it has already pulled, so run `docker pull <image>` first to test the latest push, for example `docker pull ghcr.io/srltas/cubrid-nightly:nightly`.

## License

[MIT](LICENSE) — Copyright (c) 2026 CUBRID RDBMS

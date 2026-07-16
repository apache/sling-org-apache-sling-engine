[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-engine/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-engine/job/master/)&#32;[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-engine/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-engine/job/master/test/?width=800&height=600)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-engine&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-engine)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-engine&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-engine)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.engine.svg)](https://www.javadoc.io/doc/org.apache.sling/org.apache.sling.engine)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.engine/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.engine%22) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# Apache Sling Engine Implementation

This module is part of the [Apache Sling](https://sling.apache.org) project and implements the core Sling request processing pipeline.

## Overview

`org.apache.sling.engine` provides the Sling engine bundle that:

- registers the main servlet via OSGi HTTP Whiteboard
- resolves resources and dispatches requests
- manages Sling filter chains and request/response wrapping
- exposes request processor and filter processor metrics via JMX
- supports both `javax.servlet` (legacy) and `jakarta.servlet` through adapter classes

## Requirements

- Java 17
- Maven

## Build and test

```bash
# Build and package (skip tests)
mvn clean install -DskipTests

# Full build with tests
mvn clean install

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=SlingRequestPathInfoTest

# Run tests with a name pattern
mvn test -Dtest="*FilterChain*"

# Run JMX/request stats related tests
mvn test -Dtest=RequestProcessorMBeanImplTest,ServletFilterManagerTest,SlingFilterConfigTest

# License header check (Apache RAT)
mvn apache-rat:check

# Check formatting (Spotless, inherited from parent POM)
mvn spotless:check

# Apply formatting
mvn spotless:apply

# Run Japex microbenchmarks
mvn test -Pbenchmarks
```

## Repository layout

```text
pom.xml                          Maven build descriptor
bnd.bnd                          OSGi bundle manifest instructions
src/
  main/java/org/apache/sling/engine/
    *.java                       Public API
    impl/                        Internal implementation
      adapter/                   javax <-> jakarta servlet adapters
      console/                   Web Console plugins
      debug/                     RequestProgressTracker integration
      filter/                    Sling filter chain management
      helper/                    Servlet context/request listener helpers
      log/                       Request logging support
      parameters/                Request parameter and multipart handling
      request/                   Request data and dispatching
    jmx/                         JMX MBean interfaces
    servlets/                    Error handler servlet API
  test/java/                     Unit tests
  test/resources/japex/          Benchmark configurations
```

## Notes

- OSGi Declarative Services annotations are used (`org.osgi.service.component.annotations`).
- The bundle imports `javax.servlet` with a compatibility range and supports Jakarta Servlet API in parallel.
- Filter JMX naming uses stable service properties (`sling.core.servletName`, then `service.pid`, then `component.name`, then `service.id`) and quotes names for valid JMX `ObjectName` values.
- Web Console request history rendering escapes dynamic output and handles I/O failures internally by returning HTTP 500.
- Tests use JUnit 4, with JUnit 5 Jupiter API/params available for parameterized coverage where needed.

# flat2pojo

[![Maven Central](https://img.shields.io/maven-central/v/io.github.pojotools/flat2pojo-jackson.svg?label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/io.github.pojotools/flat2pojo-jackson)
[![Build Status](https://github.com/pojotools/flat2pojo/actions/workflows/ci.yml/badge.svg)](https://github.com/pojotools/flat2pojo/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/pojotools/flat2pojo/badge.svg?branch=main)](https://coveralls.io/github/pojotools/flat2pojo?branch=main)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/projects/jdk/21/)

A high-performance Java library for converting flat maps to structured POJOs using declarative YAML configuration. Built with Jackson-first architecture for maximum compatibility and performance.

## What & Why

**flat2pojo** transforms flat key-value maps into nested object structures, perfect for:

- **Database result sets** - Convert JOIN queries to hierarchical objects
- **CSV/Excel imports** - Transform spreadsheet data to domain models
- **API responses** - Flatten external APIs then restructure for your use case
- **ETL pipelines** - High-performance data transformation with validation
- **Configuration processing** - Convert property files to structured config

### Key Features

- **Hierarchical grouping** - Convert flat rows into nested lists and objects
- **Flexible ordering** - Sort list elements by multiple fields with null handling
- **Conflict resolution** - Handle value conflicts with configurable policies
- **Type safety** - Full Jackson integration with your existing POJOs
- **Performance** - O(n) processing with minimal allocations
- **Extensible** - Custom value processing and reporting via SPI
- **Production ready** - Thread-safe, memory efficient, deterministic results

### Jackson-First Architecture

Unlike manual transformation logic, flat2pojo uses a **Jackson-first** approach: build a `JsonNode` tree, then let Jackson handle the POJO mapping with all its type conversion, annotations, and validation features. This ensures compatibility with your existing Jackson configuration and custom deserializers.

## Quick Start

### 1. Add Dependencies

```xml
<dependency>
    <groupId>io.github.pojotools</groupId>
    <artifactId>flat2pojo-jackson</artifactId>
    <version>0.4.0-SNAPSHOT</version>
</dependency>
```

For SPI extensions (optional):
```xml
<dependency>
    <groupId>io.github.pojotools</groupId>
    <artifactId>flat2pojo-spi</artifactId>
    <version>0.4.0-SNAPSHOT</version>
</dependency>
```

### 2. Define Your POJOs

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectRoot(
    String id,
    String name,
    List<Task> tasks
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record Task(
    String id,
    String title,
    String status,
    int priority,
    List<Comment> comments
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record Comment(
    String id,
    String comment
) {}
```

### 3. Create Configuration

```yaml
# mapping.yml
separator: "/"
lists:
  - path: "tasks"
    keyPaths: ["id"]
    orderBy:
      - path: "priority"
        direction: desc
    onConflict: lastWriteWins

  - path: "tasks/comments"
    keyPaths: ["id"]
    orderBy:
      - path: "timestamp"
        direction: asc

nullPolicy:
  blanksAsNulls: true
```

### 4. Transform Data

```java
// Input: flat maps from CSV, database, etc.
List<Map<String, Object>> flatData = List.of(
    Map.of("id", "proj1", "name", "Project Alpha",
           "tasks/id", "task1", "tasks/title", "Setup", "tasks/priority", "1",
           "tasks/comments/id", "c1", "tasks/comments/text", "Looks good"),
    Map.of("id", "proj1", "name", "Project Alpha",
           "tasks/id", "task1", "tasks/title", "Setup", "tasks/priority", "2",
           "tasks/comments/id", "c2", "tasks/comments/text", "Ready to deploy")
);

// Load configuration
MappingConfig config = MappingConfigLoader.fromYaml(yamlString);

// Convert to POJOs
Flat2Pojo converter = Flat2PojoFactory.create();
List<ProjectRoot> projects = converter.convertAll(flatData, ProjectRoot.class, config);
```

### Result

```json
[
  {
    "id": "proj1",
    "name": "Project Alpha",
    "tasks": [
      {
        "id": "task1",
        "title": "Setup",
        "priority": 2,
        "comments": [
          {"id": "c1", "text": "Looks good"},
          {"id": "c2", "text": "Ready to deploy"}
        ]
      }
    ]
  }
]
```

## Documentation Map

### For Users

- **[MAPPINGS.md](MAPPINGS.md)** - Complete mapping DSL specification
  - Configuration schema and YAML properties
  - Field mapping rules and path conventions
  - List rules, ordering, and deduplication
  - Conflict resolution policies
  - Validation rules and examples

- **[OPERATIONS.md](OPERATIONS.md)** - API reference and operations guide
  - API entry points (convertAll, convert, stream)
  - Performance tuning and memory management
  - Monitoring, logging, and observability
  - Troubleshooting and debugging techniques
  - Enterprise deployment patterns

### For Contributors

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Architecture and design decisions
- **[PSEUDOCODE.md](PSEUDOCODE.md)** - Internal algorithm flow and component design
- **[DEVELOPMENT.md](DEVELOPMENT.md)** - Development environment setup and build instructions
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Contribution guidelines and code standards
- **[RELEASE.md](RELEASE.md)** - Release process and versioning

### Version History

- **[CHANGELOG.md](CHANGELOG.md)** - Version history and release notes

### Refactoring Documentation

- **[docs/UNIFIED-REFACTORING-PLAN.md](docs/UNIFIED-REFACTORING-PLAN.md)** - Consolidated refactoring plan and progress

## Configuration

For complete configuration schema, field mapping rules, and examples, see **[MAPPINGS.md](MAPPINGS.md)**.

Quick overview of key configuration options:

- **separator** - Path delimiter (default: `/`)
- **rootKeys** - Fields that group rows into separate root objects
- **lists** - Hierarchical grouping with deduplication and sorting
- **primitives** - String-to-array split rules
- **nullPolicy** - Blank string handling
- **SPI extensions** - Custom preprocessing and reporting

## API Usage

See **[OPERATIONS.md](OPERATIONS.md)** for complete API reference, performance tuning, and production deployment patterns.

Quick API overview:

```java
// Batch conversion (recommended)
List<MyPojo> results = converter.convertAll(rows, MyPojo.class, config);

// Single row with null safety
Optional<MyPojo> result = converter.convertOptional(row, MyPojo.class, config);

// Streaming for large datasets
Stream<MyPojo> stream = converter.stream(rowIterator, MyPojo.class, config);
```

## Development

See **[DEVELOPMENT.md](DEVELOPMENT.md)** for complete development setup, build instructions, and code quality tools.

Quick start for contributors:

```bash
git clone https://github.com/pojotools/flat2pojo.git
cd flat2pojo
mvn clean verify
```

## License

MIT License - See [LICENSE](LICENSE) file for details.
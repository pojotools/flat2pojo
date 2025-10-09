# flat2pojo

A high-performance Java library for converting flat maps to structured POJOs using declarative YAML configuration. Built with Jackson-first architecture for maximum compatibility and performance.

## What & Why

**flat2pojo** transforms flat key-value maps into nested object structures, perfect for:

- **Database result sets** - Convert JOIN queries to hierarchical objects
- **CSV/Excel imports** - Transform spreadsheet data to domain models
- **API responses** - Flatten external APIs then restructure for your use case
- **ETL pipelines** - High-performance data transformation with validation
- **Configuration processing** - Convert property files to structured config

### Key Features

- **🏗️ Hierarchical grouping** - Convert flat rows into nested lists and objects
- **🔀 Flexible ordering** - Sort list elements by multiple fields with null handling
- **⚔️ Conflict resolution** - Handle value conflicts with configurable policies
- **🔒 Type safety** - Full Jackson integration with your existing POJOs
- **🚀 Performance** - O(n) processing with minimal allocations
- **🔧 Extensible** - Custom value processing and reporting via SPI
- **📊 Production ready** - Thread-safe, memory efficient, deterministic results

### Jackson-First Architecture

Unlike manual transformation logic, flat2pojo uses a **Jackson-first** approach: build a `JsonNode` tree, then let Jackson handle the POJO mapping with all its type conversion, annotations, and validation features. This ensures compatibility with your existing Jackson configuration and custom deserializers.

## Quick Start

### 1. Add Dependencies

```xml
<dependency>
    <groupId>io.github.pojotools</groupId>
    <artifactId>flat2pojo-jackson</artifactId>
    <version>0.3.0</version>
</dependency>
```

For SPI extensions (optional):
```xml
<dependency>
    <groupId>io.github.pojotools</groupId>
    <artifactId>flat2pojo-spi</artifactId>
    <version>0.3.0</version>
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
    List<Comment> comments
) {}
```

### 3. Create Configuration

```yaml
# mapping.yml
separator: "/"
lists:
  - path: "tasks"
    keyPaths: ["tasks/id"]
    orderBy:
      - path: "tasks/priority"
        direction: desc
    onConflict: lastWriteWins

  - path: "tasks/comments"
    keyPaths: ["tasks/comments/id"]
    orderBy:
      - path: "tasks/comments/timestamp"
        direction: asc

nullPolicy:
  blanksAsNulls: true
```

### 4. Transform Data

```java
// Input: flat maps from CSV, database, etc.
List<Map<String, Object>> flatData = List.of(
    Map.of("id", "proj1", "name", "Project Alpha",
           "tasks/id", "task1", "tasks/title", "Setup",
           "tasks/comments/id", "c1", "tasks/comments/text", "Looks good"),
    Map.of("id", "proj1", "name", "Project Alpha",
           "tasks/id", "task1", "tasks/title", "Setup",
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
        "comments": [
          {"id": "c1", "text": "Looks good"},
          {"id": "c2", "text": "Ready to deploy"}
        ]
      }
    ]
  }
]
```

## Documentation

### Core Documentation

- **[MAPPING.md](MAPPING.md)** - Complete configuration reference
  - Configuration schema and semantic rules
  - Field mapping and path conventions
  - List rules, ordering, and deduplication
  - Conflict resolution policies
  - Validation rules and best practices

- **[OPERATIONS.md](OPERATIONS.md)** - Production operations guide
  - API reference and entry points
  - Performance optimization strategies
  - Monitoring and observability patterns
  - Troubleshooting and debugging
  - Enterprise deployment best practices

- **[PSEUDOCODE.md](PSEUDOCODE.md)** - Internal architecture (for contributors)
  - Algorithm flow and pseudocode
  - Component interactions and data flow
  - Performance characteristics
  - Design rationale

### Additional Resources

- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Contribution guidelines
- **[RELEASE.md](RELEASE.md)** - Release process and versioning

## Configuration Reference

For complete configuration details, see [MAPPING.md](MAPPING.md).

### Complete Configuration Structure

```yaml
# Basic path configuration
separator: "/"                    # Path separator (default: "/")
allowSparseRows: false           # Allow incomplete rows (default: false)
rootKeys: []                     # Fields that group root-level objects

# Hierarchical data configuration
lists:
  - path: "parent/children"           # Path to the list
    keyPaths: ["parent/children/id"]  # Fields that identify unique elements
    orderBy:                          # Sorting specification
      - path: "parent/children/priority"
        direction: asc                # asc|desc
        nulls: last                   # first|last
    dedupe: true                      # Remove duplicates (default: true)
    onConflict: lastWriteWins         # error|firstWriteWins|lastWriteWins|merge

# String transformation
primitives:
  - path: "tags"
    delimiter: ","
    trim: true

# Data quality configuration
nullPolicy:
  blanksAsNulls: true              # Convert empty strings to null

# Extensibility (SPI - optional)
reporter: !custom                  # Custom reporter implementation
valuePreprocessor: !custom        # Custom value preprocessor
```

### Root Keys

Group flat rows into separate root-level objects:

```yaml
rootKeys: ["projectId"]
```

**Input:**
```
projectId=proj1, name=Alpha, tasks/id=t1, tasks/title=Setup
projectId=proj1, name=Alpha, tasks/id=t2, tasks/title=Deploy
projectId=proj2, name=Beta, tasks/id=t3, tasks/title=Test
```

**Output:**
```json
[
  {
    "projectId": "proj1", "name": "Alpha",
    "tasks": [
      {"id": "t1", "title": "Setup"},
      {"id": "t2", "title": "Deploy"}
    ]
  },
  {
    "projectId": "proj2", "name": "Beta",
    "tasks": [{"id": "t3", "title": "Test"}]
  }
]
```

### List Rules

Configure hierarchical grouping and sorting:

```yaml
lists:
  - path: "tasks"                     # List location
    keyPaths: ["tasks/id"]            # Unique identifier fields
    orderBy:                          # Multi-level sorting
      - path: "priority"              # Sort field (relative to element)
        direction: desc               # asc|desc
        nulls: last                   # first|last
      - path: "created"               # Secondary sort
        direction: asc
    dedupe: true                      # Remove duplicates
    onConflict: lastWriteWins         # Conflict resolution policy
```

**Conflict Policies:**
- `error`: Throw exception on conflicts
- `firstWriteWins`: Keep first value encountered
- `lastWriteWins`: Use most recent value
- `merge`: Deep merge objects, overwrite scalars

### Primitive Splits

Transform delimited strings into arrays:

```yaml
primitives:
  - path: "tags"
    delimiter: ","
    trim: true
  - path: "coordinates"
    delimiter: "|"
    trim: false
```

### Advanced Options

```yaml
# Data sparsity handling
allowSparseRows: true            # Allow rows missing list keyPaths

# Custom path separators
separator: "."                   # Use dots instead of slashes

# Null value handling
nullPolicy:
  blanksAsNulls: true           # "" becomes null in JSON
```

## Extensibility (SPI)

flat2pojo provides Service Provider Interfaces (SPI) for custom processing and monitoring:

### Value Preprocessing

Transform input data before conversion:

```java
import io.github.pojotools.flat2pojo.spi.ValuePreprocessor;

// Convert YES/NO to boolean values
ValuePreprocessor preprocessor = row -> {
    Map<String, Object> processed = new HashMap<>(row);
    processed.forEach((key, value) -> {
        if ("YES".equals(value)) {
            processed.put(key, true);
        } else if ("NO".equals(value)) {
            processed.put(key, false);
        }
    });
    return processed;
};

// Use with configuration
MappingConfig config = MappingConfig.builder()
    .separator("/")
    .valuePreprocessor(Optional.of(preprocessor))
    .lists(/* your lists */)
    .build();
```

### Conversion Monitoring

Monitor conversion process and capture warnings:

```java
import io.github.pojotools.flat2pojo.spi.Reporter;

// Capture all warnings
List<String> warnings = new ArrayList<>();
Reporter reporter = warnings::add;

MappingConfig config = MappingConfig.builder()
    .separator("/")
    .reporter(Optional.of(reporter))
    .lists(/* your lists */)
    .build();

// After conversion, check for issues
List<MyPojo> results = converter.convertAll(data, MyPojo.class, config);
if (!warnings.isEmpty()) {
    warnings.forEach(System.err::println);
}
```

### Common SPI Use Cases

**Data Normalization:**
```java
// Normalize phone numbers, emails, etc.
ValuePreprocessor normalizer = row -> {
    Map<String, Object> normalized = new HashMap<>(row);
    normalized.forEach((key, value) -> {
        if (key.contains("phone") && value instanceof String phone) {
            normalized.put(key, normalizePhoneNumber(phone));
        }
    });
    return normalized;
};
```

**Audit Trail:**
```java
// Log all conflicts and skipped data
Reporter auditReporter = warning -> {
    logger.warn("Data quality issue: {}", warning);
    auditService.recordDataIssue(warning);
};
```

**Combined Usage:**
```java
MappingConfig config = MappingConfig.builder()
    .separator("/")
    .valuePreprocessor(Optional.of(dataCleaningPreprocessor))
    .reporter(Optional.of(auditReporter))
    .lists(listRules)
    .build();
```

### SPI Warnings Captured

The Reporter interface captures these types of warnings:

- **Missing KeyPaths**: `"Skipping list rule 'tasks' because keyPath(s) [tasks/id] are missing or null"`
- **Field Conflicts**: `"Field conflict resolved using lastWriteWins policy at 'user/email': replaced existing="old@email.com" with incoming="new@email.com""`
- **Skipped Hierarchies**: `"Skipping list rule 'projects/tasks' because parent list was skipped"`

## Performance Tips

• **Reuse configuration objects** - `MappingConfig` instances are immutable and thread-safe
• **Use appropriate batch sizes** - Process 1K-10K rows per batch for optimal memory usage
• **Precompile your config** - Load YAML once, reuse the `MappingConfig`
• **Consider streaming** - Use `converter.stream()` for very large datasets
• **Optimize Jackson mapper** - Reuse the same `ObjectMapper` instance

For detailed performance optimization strategies, see [OPERATIONS.md](OPERATIONS.md#performance-optimization).

## Jackson Integration

flat2pojo uses the standard Jackson `JsonMapper` with optimized settings:

```java
JsonMapper mapper = JsonMapper.builder()
    .addModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build();
```

This ensures compatibility with:
- `java.time` types (LocalDate, Instant, etc.)
- Case-insensitive enums
- Flexible field mapping with unknown properties ignored
- ISO-8601 date formatting

Use `@JsonIgnoreProperties(ignoreUnknown = true)` on your POJOs for maximum flexibility.

## Integration Guide

### Common Integration Patterns

**Spring Boot Configuration:**
```java
@Configuration
public class Flat2PojoConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return JacksonAdapter.defaultObjectMapper();
    }

    @Bean
    public Flat2Pojo flat2PojoConverter(ObjectMapper objectMapper) {
        return new Flat2PojoCore(objectMapper);
    }

    @Bean
    public MappingConfig projectMappingConfig() {
        return MappingConfigLoader.fromResource("mappings/project-mapping.yml");
    }
}
```

**Database Integration (JdbcTemplate):**
```java
@Service
public class ProjectService {

    private final JdbcTemplate jdbcTemplate;
    private final Flat2Pojo converter;
    private final MappingConfig config;

    public List<Project> getProjectsWithTasks() {
        String sql = """
            SELECT p.id as id, p.name as name,
                   t.id as tasks/id, t.title as tasks/title,
                   c.id as tasks/comments/id, c.text as tasks/comments/text
            FROM projects p
            LEFT JOIN tasks t ON p.id = t.project_id
            LEFT JOIN comments c ON t.id = c.task_id
            ORDER BY p.id, t.id, c.id
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return converter.convertAll(rows, Project.class, config);
    }
}
```

**CSV Processing:**
```java
@Component
public class CsvProcessor {

    private final Flat2Pojo converter;

    public <T> List<T> processCsv(InputStream csvStream, Class<T> targetType,
                                  MappingConfig config) throws IOException {

        // Read CSV with headers
        List<Map<String, Object>> rows = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(csvStream))) {
            String[] headers = reader.readNext();
            String[] line;

            while ((line = reader.readNext()) != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < line.length; i++) {
                    row.put(headers[i], line[i]);
                }
                rows.add(row);
            }
        }

        return converter.convertAll(rows, targetType, config);
    }
}
```

**REST API Integration:**
```java
@RestController
public class DataTransformController {

    private final Flat2Pojo converter;

    @PostMapping("/transform")
    public ResponseEntity<?> transformData(
            @RequestBody List<Map<String, Object>> flatData,
            @RequestParam String mappingName) {

        try {
            MappingConfig config = loadMappingConfig(mappingName);

            // Use Reporter to capture data quality issues
            List<String> warnings = new ArrayList<>();
            config = config.withReporter(Optional.of(warnings::add));

            List<JsonNode> results = converter.convertAll(flatData, JsonNode.class, config);

            return ResponseEntity.ok(Map.of(
                "results", results,
                "warnings", warnings,
                "recordCount", results.size()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                Map.of("error", e.getMessage())
            );
        }
    }
}
```

### Troubleshooting Common Issues

**Issue: Lists appear empty**
```yaml
# ❌ Wrong: keyPaths don't match data
lists:
  - path: "tasks"
    keyPaths: ["task_id"]  # But data has "tasks/id"

# ✅ Correct: Match exact field names in data
lists:
  - path: "tasks"
    keyPaths: ["tasks/id"]
```

**Issue: Jackson deserialization errors**
```java
// Use JsonNode first to debug structure
List<JsonNode> nodes = converter.convertAll(data, JsonNode.class, config);
System.out.println(nodes.get(0).toPrettyString());
```

For comprehensive troubleshooting guidance, advanced debugging techniques, and production monitoring patterns, see [OPERATIONS.md](OPERATIONS.md#troubleshooting).

## Development

### Building from Source

```bash
git clone https://github.com/pojotools/flat2pojo.git
cd flat2pojo
mvn clean verify
```

### Code Quality

This project maintains high code quality standards through comprehensive static analysis:

#### Static Analysis Tools

- **Checkstyle** - Enforces Clean Code principles (Uncle Bob) including:
  - Method complexity ≤15, length ≤50 lines, ≤6 parameters
  - Mandatory braces, switch defaults, proper imports
  - Line length ≤120 characters, proper naming conventions
- **SpotBugs** - Identifies potential bugs and security vulnerabilities through static analysis
- **ErrorProne** - Google's compile-time checker that catches common Java programming mistakes
- **JaCoCo** - Tracks test coverage across modules with 60% minimum threshold
  - Cross-module coverage aggregation via dedicated `flat2pojo-coverage` module
  - Tests in `flat2pojo-examples` exercise code in `flat2pojo-core` and `flat2pojo-jackson`
  - HTML reports available at `flat2pojo-coverage/target/site/jacoco-aggregate/index.html`

The checkstyle configuration follows **Clean Code principles** by Uncle Bob Martin, enforcing small functions, low complexity, and defensive coding practices while being pragmatic for existing codebases.

#### Running Quality Checks

```bash
# Full build with all static analysis
mvn clean verify

# Run specific tools
mvn checkstyle:check          # Style validation
mvn spotbugs:check           # Bug detection
mvn jacoco:report            # Coverage report
mvn spotless:apply           # Auto-format code

# View aggregated coverage report
open flat2pojo-coverage/target/site/jacoco-aggregate/index.html
```

#### Code Formatting

The project uses **Spotless** with Google Java Format:

```bash
# Apply formatting
mvn spotless:apply

# Check formatting
mvn spotless:check
```

All static analysis tools are configured to run automatically during the build process, ensuring consistent code quality across all contributions.
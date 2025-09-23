# flat2pojo

A high-performance Java library for converting flat maps to structured POJOs using declarative YAML configuration. Built with Jackson-first architecture for maximum compatibility and performance.

## What & Why

**flat2pojo** transforms flat key-value maps into nested object structures, with first-class support for:

- **Hierarchical grouping** - Convert related flat rows into nested lists and objects
- **Flexible ordering** - Sort list elements by multiple fields with null handling
- **Conflict resolution** - Handle value conflicts with configurable policies
- **Type safety** - Full Jackson integration with your existing POJOs
- **Performance** - O(n) processing with minimal allocations

Unlike manual transformation logic, flat2pojo uses a **Jackson-first** approach: build a `JsonNode` tree, then let Jackson handle the POJO mapping with all its type conversion, annotations, and validation features.

## Quick Start

### 1. Add Dependencies

```xml
<dependency>
    <groupId>io.flat2pojo</groupId>
    <artifactId>flat2pojo-jackson</artifactId>
    <version>0.1.0-SNAPSHOT</version>
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

## Configuration Reference

### List Rules

```yaml
lists:
  - path: "parent/children"           # Path to the list
    keyPaths: ["parent/children/id"]  # Fields that identify unique elements
    orderBy:                          # Sorting specification
      - path: "parent/children/priority"
        direction: asc                # asc|desc
        nulls: last                   # first|last
    dedupe: true                      # Remove duplicates (default: true)
    onConflict: lastWriteWins         # error|firstWriteWins|lastWriteWins|merge
```

### Primitive Splits

```yaml
primitives:
  - path: "tags"
    split:
      delimiter: ","
      trim: true
```

### Null Policy

```yaml
nullPolicy:
  blanksAsNulls: true  # Convert empty strings to null
```

## Performance Tips

• **Reuse configuration objects** - `MappingConfig` instances are immutable and thread-safe
• **Use appropriate batch sizes** - Process 1K-10K rows per batch for optimal memory usage
• **Precompile your config** - Load YAML once, reuse the `MappingConfig`
• **Consider streaming** - Use `converter.stream()` for very large datasets
• **Optimize Jackson mapper** - Reuse the same `ObjectMapper` instance

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
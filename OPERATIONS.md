# Operations Guide

This document covers operational aspects of using flat2pojo in production environments.

## Processing Modes

### Batch Processing (Recommended)

Process multiple rows at once for optimal performance:

```java
List<Map<String, Object>> batchData = loadBatch(1000);
List<MyPojo> results = converter.convertAll(batchData, MyPojo.class, config);
```

**Benefits:**
- Optimal memory usage through grouped processing
- Efficient list element deduplication
- Better Jackson object reuse

**Recommended batch sizes:**
- **Small objects**: 5,000-10,000 rows
- **Medium complexity**: 1,000-5,000 rows
- **Deep nesting**: 500-2,000 rows
- **Memory constrained**: 100-1,000 rows

### Streaming Processing

For very large datasets that don't fit in memory:

```java
Iterator<Map<String, Object>> rowIterator = getLargeDataset();
Stream<MyPojo> results = converter.stream(rowIterator, MyPojo.class, config);

// Process results incrementally
results.forEach(this::processResult);
```

**Use cases:**
- Datasets larger than available heap
- Real-time processing pipelines
- ETL transformations

**Limitations:**
- Less efficient than batching
- No cross-batch deduplication
- Higher per-row overhead

### Single Row Processing

For individual conversions or testing:

```java
Map<String, Object> singleRow = getSingleRow();
MyPojo result = converter.convert(singleRow, MyPojo.class, config);
```

## Memory Management

### Configuration Caching

`MappingConfig` objects are immutable and thread-safe. Cache them for reuse:

```java
// ✅ Good: Cache configuration
private static final MappingConfig CONFIG = MappingConfigLoader.fromYaml(yamlString);

public List<MyPojo> processData(List<Map<String, Object>> data) {
    return converter.convertAll(data, MyPojo.class, CONFIG);
}
```

```java
// ❌ Bad: Recreate configuration each time
public List<MyPojo> processData(List<Map<String, Object>> data) {
    MappingConfig config = MappingConfigLoader.fromYaml(yamlString); // Wasteful
    return converter.convertAll(data, MyPojo.class, config);
}
```

### Jackson Mapper Reuse

Share `ObjectMapper` instances across conversions:

```java
// ✅ Good: Reuse mapper
private static final ObjectMapper MAPPER = JacksonAdapter.defaultObjectMapper();
private static final Flat2Pojo CONVERTER = new Flat2PojoCore(MAPPER);

// ❌ Bad: Create new mapper each time
public void processData() {
    Flat2Pojo converter = Flat2PojoFactory.create(); // Creates new mapper
}
```

### Memory Knobs

Control memory usage through configuration:

```yaml
# Smaller batch sizes reduce peak memory
# Larger batches improve throughput
separator: "/"

# Minimize list rules for lower overhead
lists:
  - path: "essential_list_only"
    keyPaths: ["essential_list_only/id"]
    # Avoid complex orderBy when not needed
```

## Performance Optimization

### Hot Path Optimizations

flat2pojo includes several performance optimizations:

1. **Index-based path traversal** - No `String.split()` in loops
2. **Precomputed separators** - Cached separator characters
3. **Comparator reuse** - Built once per list rule
4. **Direct node creation** - Avoid `ObjectMapper.valueToTree()` for primitives

### Avoiding Performance Pitfalls

```java
// ✅ Good: Batch processing
List<Map<String, Object>> batch = loadBatch(1000);
converter.convertAll(batch, MyPojo.class, config);

// ❌ Bad: Row-by-row processing
for (Map<String, Object> row : rows) {
    converter.convert(row, MyPojo.class, config); // Inefficient
}
```

```yaml
# ✅ Good: Simple separators
separator: "/"

# ❌ Avoid: Complex multi-character separators in hot paths
separator: "<-->"
```

### Profiling Guidelines

Monitor these metrics in production:

- **Memory allocation rate** - Should be low with proper batching
- **GC pressure** - Minimize with object reuse
- **CPU utilization** - Optimize with appropriate batch sizes
- **Conversion latency** - Target <100ms for typical batches

## Thread Safety

### Thread-Safe Components

- ✅ `MappingConfig` - Immutable after creation
- ✅ `ObjectMapper` - Thread-safe when properly configured
- ✅ `Flat2Pojo` instance - Stateless conversion logic

### Per-Thread State

These components maintain per-conversion state and are **not** thread-safe:

- ❌ `GroupingEngine` - Contains mutable buckets
- ❌ `FlatTreeBuilder` - Maintains conversion state
- ❌ Internal caches during conversion

### Recommended Patterns

```java
// ✅ Pattern 1: Shared converter (recommended)
private static final Flat2Pojo CONVERTER = Flat2PojoFactory.create();

@Async
public CompletableFuture<List<MyPojo>> processAsync(List<Map<String, Object>> data) {
    return CompletableFuture.supplyAsync(() ->
        CONVERTER.convertAll(data, MyPojo.class, config));
}
```

```java
// ✅ Pattern 2: Per-thread converters
private static final ThreadLocal<Flat2Pojo> THREAD_CONVERTER =
    ThreadLocal.withInitial(Flat2PojoFactory::create);

public List<MyPojo> processData(List<Map<String, Object>> data) {
    return THREAD_CONVERTER.get().convertAll(data, MyPojo.class, config);
}
```

## Determinism Guarantees

### Ordering Guarantees

flat2pojo provides predictable ordering behavior:

1. **Root objects**: Stable order based on input sequence
2. **List elements**: Deterministic based on `orderBy` rules
3. **Field processing**: Consistent iteration order
4. **Conflict resolution**: Predictable based on policy

### Reproducible Results

Given identical input data and configuration:

- ✅ **Same output structure** - Always produces identical JSON trees
- ✅ **Same field values** - Conflict resolution is deterministic
- ✅ **Same ordering** - List sorting is stable and consistent
- ✅ **Same validation** - Errors occur at same points

### Non-Deterministic Scenarios

Be aware of these edge cases:

- **HashMap iteration order** - Use `LinkedHashMap` for input data
- **Concurrent modifications** - Don't modify input during conversion
- **System-dependent sorting** - Locale-specific string comparisons

## Troubleshooting

### Common Validation Errors

**Error**: `List 'tasks' must be declared after its parent list 'projects'`
```yaml
# Fix: Reorder list declarations
lists:
  - path: "projects"      # Parent first
    keyPaths: ["projects/id"]
  - path: "projects/tasks" # Child second
    keyPaths: ["projects/tasks/id"]
```

**Error**: `Invalid list rule: 'tasks' missing ancestor 'projects'`
```yaml
# Fix: Add missing parent list
lists:
  - path: "projects"      # Add missing parent
    keyPaths: ["projects/id"]
  - path: "tasks"
    keyPaths: ["projects/tasks/id"]  # This keyPath implies 'projects' exists
```

### Memory Issues

**OutOfMemoryError during conversion:**
1. Reduce batch size
2. Check for memory leaks in input data
3. Verify Jackson configuration
4. Profile object allocation

### Performance Issues

**Slow conversion times:**
1. Profile with JFR or similar tools
2. Check for inefficient orderBy rules
3. Verify appropriate batch sizes
4. Consider simpler separator characters

**High GC pressure:**
1. Increase batch sizes (within memory limits)
2. Reuse converter instances
3. Pool ObjectMapper instances
4. Profile allocation hotspots

### Data Quality Issues

**Missing list elements:**
1. Verify keyPaths are correct
2. Check for data type mismatches
3. Validate input data completeness
4. Review conflict policies

**Unexpected merging behavior:**
1. Check onConflict settings
2. Verify keyPaths uniqueness
3. Review input data for duplicates
4. Consider dedupe settings

### Debugging Tips

Enable detailed logging:

```java
// Add to logback.xml or log4j2.xml
Logger logger = LoggerFactory.getLogger("io.flat2pojo");
logger.setLevel(Level.DEBUG);
```

Use JsonNode output for debugging:

```java
// Convert to JsonNode to inspect structure
List<JsonNode> nodes = converter.convertAll(data, JsonNode.class, config);
System.out.println(nodes.get(0).toPrettyString());
```

Validate configuration separately:

```java
try {
    MappingConfigLoader.validateHierarchy(config);
    System.out.println("Configuration is valid");
} catch (ValidationException e) {
    System.err.println("Configuration error: " + e.getMessage());
}
```
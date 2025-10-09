# Operations Guide

This document covers operational aspects of using flat2pojo in production environments, including API usage, performance optimization, monitoring, troubleshooting, and best practices for enterprise deployments.

## Related Documentation

- [MAPPING.md](MAPPING.md) - Configuration schema, field mapping rules, and semantic constraints
- [PSEUDOCODE.md](PSEUDOCODE.md) - Internal algorithm flow, component architecture, and performance characteristics
- [README.md](README.md) - Project overview, quick start guide, and getting started

## API Entry Points

The `Flat2Pojo` interface provides several entry points for data conversion:

### convertAll(rows, targetType, config)

**Primary batch conversion method** - converts a list of flat maps to POJOs:

```java
List<Map<String, Object>> rows = loadData();
MappingConfig config = MappingConfigLoader.fromYaml(yamlString);
Flat2Pojo converter = Flat2PojoFactory.create();

List<MyPojo> results = converter.convertAll(rows, MyPojo.class, config);
```

**Use when:**
- Processing multiple rows as a batch (recommended)
- Grouping is configured via `rootKeys`
- Optimal performance is required

### convertOptional(row, targetType, config)

**Safe single row conversion** - returns `Optional<T>`:

```java
Map<String, Object> row = getSingleRow();
Optional<MyPojo> result = converter.convertOptional(row, MyPojo.class, config);
result.ifPresent(this::processResult);
```

**Use when:**
- Null safety is important
- Handling potentially empty results
- Functional programming patterns

### stream(iterator, targetType, config)

**Streaming conversion** - processes data incrementally:

```java
Iterator<Map<String, Object>> rowIterator = getLargeDataset();
Stream<MyPojo> results = converter.stream(rowIterator, MyPojo.class, config);

results.forEach(this::processResult);
```

**Use when:**
- Dataset too large for memory
- Real-time processing pipelines
- ETL transformations

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
Optional<MyPojo> result = converter.convertOptional(singleRow, MyPojo.class, config);
result.ifPresent(this::processResult);
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
    converter.convertOptional(row, MyPojo.class, config); // Inefficient - use batch instead
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

## Production Monitoring & Observability

### SPI-Based Monitoring

The Reporter SPI provides comprehensive visibility into conversion processes:

#### Metrics Collection

```java
public class MetricsReporter implements Reporter {
    private final MeterRegistry meterRegistry;
    private final Counter warningCounter;
    private final Counter conflictCounter;
    private final Counter skippedListCounter;

    public MetricsReporter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.warningCounter = Counter.builder("flat2pojo.warnings")
            .description("Total conversion warnings")
            .register(meterRegistry);
        this.conflictCounter = Counter.builder("flat2pojo.conflicts")
            .description("Field conflicts resolved")
            .register(meterRegistry);
        this.skippedListCounter = Counter.builder("flat2pojo.skipped_lists")
            .description("Lists skipped due to missing keyPaths")
            .register(meterRegistry);
    }

    @Override
    public void warn(String message) {
        warningCounter.increment();

        if (message.contains("conflict")) {
            conflictCounter.increment();
        } else if (message.contains("Skipping list rule")) {
            skippedListCounter.increment();
        }

        // Log for detailed troubleshooting
        logger.warn("Data quality issue: {}", message);
    }
}
```

#### Health Checking

```java
@Component
public class Flat2PojoHealthIndicator implements HealthIndicator {

    private final AtomicLong lastConversionTime = new AtomicLong(0);
    private final AtomicLong conversionCount = new AtomicLong(0);
    private final AtomicLong warningCount = new AtomicLong(0);

    private final Reporter healthReporter = warning -> {
        warningCount.incrementAndGet();
        lastConversionTime.set(System.currentTimeMillis());
    };

    @Override
    public Health health() {
        long timeSinceLastConversion = System.currentTimeMillis() - lastConversionTime.get();
        long totalConversions = conversionCount.get();
        long totalWarnings = warningCount.get();

        Health.Builder builder = new Health.Builder();

        if (totalConversions > 0) {
            double warningRate = (double) totalWarnings / totalConversions;

            if (warningRate < 0.1) {
                builder.up();
            } else if (warningRate < 0.3) {
                builder.status("WARNING");
            } else {
                builder.down();
            }

            builder.withDetail("totalConversions", totalConversions)
                   .withDetail("totalWarnings", totalWarnings)
                   .withDetail("warningRate", String.format("%.2f%%", warningRate * 100))
                   .withDetail("timeSinceLastConversion", timeSinceLastConversion + "ms");
        } else {
            builder.status("UNKNOWN").withDetail("status", "No conversions performed yet");
        }

        return builder.build();
    }

    public Reporter getHealthReporter() {
        return warning -> {
            healthReporter.warn(warning);
            conversionCount.incrementAndGet();
        };
    }
}
```

#### Detailed Audit Logging

```java
public class AuditTrailReporter implements Reporter {
    private static final Logger auditLogger = LoggerFactory.getLogger("FLAT2POJO_AUDIT");

    @Override
    public void warn(String message) {
        Map<String, Object> auditEvent = new LinkedHashMap<>();
        auditEvent.put("timestamp", Instant.now().toString());
        auditEvent.put("level", "WARN");
        auditEvent.put("category", categorizeWarning(message));
        auditEvent.put("message", message);
        auditEvent.put("thread", Thread.currentThread().getName());

        // Log as structured JSON for log aggregation
        try {
            ObjectMapper mapper = new ObjectMapper();
            auditLogger.warn(mapper.writeValueAsString(auditEvent));
        } catch (Exception e) {
            auditLogger.warn("Audit logging failed: {} - Original message: {}", e.getMessage(), message);
        }
    }

    private String categorizeWarning(String message) {
        if (message.contains("conflict")) return "FIELD_CONFLICT";
        if (message.contains("Skipping list rule")) return "MISSING_KEYPATH";
        if (message.contains("parent list")) return "HIERARCHY_SKIP";
        return "GENERAL";
    }
}
```

### Data Quality Monitoring

```java
@Service
public class ConversionQualityService {

    public ConversionResult convertWithQualityAnalysis(
            List<Map<String, Object>> data,
            Class<?> targetType,
            MappingConfig baseConfig) {

        // Capture detailed quality metrics
        List<String> warnings = new ArrayList<>();
        Map<String, AtomicLong> categoryCounters = new HashMap<>();

        Reporter qualityReporter = warning -> {
            warnings.add(warning);
            String category = categorizeIssue(warning);
            categoryCounters.computeIfAbsent(category, k -> new AtomicLong(0)).incrementAndGet();
        };

        MappingConfig monitoredConfig = baseConfig.withReporter(Optional.of(qualityReporter));

        // Perform conversion with timing
        long startTime = System.currentTimeMillis();
        List<?> results = converter.convertAll(data, targetType, monitoredConfig);
        long duration = System.currentTimeMillis() - startTime;

        // Analyze quality
        QualityMetrics metrics = QualityMetrics.builder()
            .inputRowCount(data.size())
            .outputObjectCount(results.size())
            .totalWarnings(warnings.size())
            .categoryBreakdown(categoryCounters)
            .processingTimeMs(duration)
            .dataQualityScore(calculateQualityScore(warnings.size(), data.size()))
            .build();

        return new ConversionResult(results, warnings, metrics);
    }

    private double calculateQualityScore(int warningCount, int inputSize) {
        if (inputSize == 0) return 1.0;
        double warningRatio = (double) warningCount / inputSize;
        return Math.max(0.0, 1.0 - (warningRatio * 2)); // Penalty for warnings
    }
}
```

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

## Production Deployment Patterns

### Enterprise Configuration Management

```java
@Configuration
@EnableConfigurationProperties(Flat2PojoProperties.class)
public class Flat2PojoProductionConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    @Bean
    public Reporter productionReporter(MeterRegistry meterRegistry,
                                     AuditService auditService) {
        return new CompositeReporter(
            new MetricsReporter(meterRegistry),
            new AuditTrailReporter(),
            new AlertingReporter(auditService)
        );
    }

    @Bean
    public ValuePreprocessor standardPreprocessor() {
        return new ChainedValuePreprocessor(
            new NormalizationPreprocessor(),
            new ValidationPreprocessor(),
            new SanitizationPreprocessor()
        );
    }

    @Bean
    public MappingConfig applicationMappingConfig(Flat2PojoProperties properties,
                                                Reporter reporter,
                                                ValuePreprocessor preprocessor) {
        return MappingConfigLoader.fromResource(properties.getConfigPath())
            .withReporter(Optional.of(reporter))
            .withValuePreprocessor(Optional.of(preprocessor))
            .withNullPolicy(new NullPolicy(properties.isBlanksAsNulls()));
    }
}
```

### Circuit Breaker Integration

```java
@Component
public class ResilientConversionService {

    private final CircuitBreaker circuitBreaker;
    private final Flat2Pojo converter;

    public ResilientConversionService(Flat2Pojo converter) {
        this.converter = converter;
        this.circuitBreaker = CircuitBreaker.ofDefaults("flat2pojo-conversion");

        // Configure circuit breaker
        circuitBreaker.getEventPublisher().onStateTransition(
            event -> logger.info("Circuit breaker state transition: {}", event));
    }

    public <T> List<T> convertWithResilience(List<Map<String, Object>> data,
                                           Class<T> targetType,
                                           MappingConfig config) {
        return circuitBreaker.executeSupplier(() -> {
            try {
                return converter.convertAll(data, targetType, config);
            } catch (Exception e) {
                logger.error("Conversion failed for {} rows", data.size(), e);
                throw e;
            }
        });
    }
}
```

### Caching Strategy

```java
@Service
public class CachedConversionService {

    private final LoadingCache<ConfigCacheKey, MappingConfig> configCache;
    private final Flat2Pojo converter;

    public CachedConversionService(Flat2Pojo converter) {
        this.converter = converter;
        this.configCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .recordStats()
            .build(this::loadMappingConfig);
    }

    public <T> List<T> convert(List<Map<String, Object>> data,
                              Class<T> targetType,
                              String configName) {
        try {
            MappingConfig config = configCache.get(new ConfigCacheKey(configName));
            return converter.convertAll(data, targetType, config);
        } catch (ExecutionException e) {
            throw new ConversionException("Failed to load configuration: " + configName, e);
        }
    }

    private MappingConfig loadMappingConfig(ConfigCacheKey key) {
        return MappingConfigLoader.fromResource("mappings/" + key.name() + ".yml");
    }

    @EventListener
    public void onConfigurationChanged(ConfigurationChangedEvent event) {
        configCache.invalidate(new ConfigCacheKey(event.getConfigName()));
        logger.info("Invalidated cached configuration: {}", event.getConfigName());
    }
}
```

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

### Advanced Debugging Techniques

#### SPI-Based Diagnostics

```java
public class DiagnosticReporter implements Reporter {
    private final Map<String, AtomicLong> issueFrequency = new ConcurrentHashMap<>();
    private final List<String> recentWarnings = new ArrayList<>();
    private final int maxRecentWarnings = 100;

    @Override
    public void warn(String message) {
        // Track frequency of different issue types
        String category = categorizeMessage(message);
        issueFrequency.computeIfAbsent(category, k -> new AtomicLong(0)).incrementAndGet();

        // Keep recent warnings for analysis
        synchronized (recentWarnings) {
            recentWarnings.add(message);
            if (recentWarnings.size() > maxRecentWarnings) {
                recentWarnings.remove(0);
            }
        }

        logger.debug("Conversion diagnostic: {}", message);
    }

    public Map<String, Long> getIssueFrequency() {
        return issueFrequency.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get()));
    }

    public List<String> getRecentWarnings() {
        synchronized (recentWarnings) {
            return new ArrayList<>(recentWarnings);
        }
    }
}
```

#### Value Preprocessing Debugging

```java
public class DebuggingValuePreprocessor implements ValuePreprocessor {
    private final ValuePreprocessor delegate;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public DebuggingValuePreprocessor(ValuePreprocessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public Map<String, Object> process(Map<String, ?> row) {
        Map<String, Object> originalRow = new HashMap<>(row);
        Map<String, Object> processedRow = delegate.process(row);

        // Log transformations
        Set<String> allKeys = new HashSet<>(originalRow.keySet());
        allKeys.addAll(processedRow.keySet());

        for (String key : allKeys) {
            Object original = originalRow.get(key);
            Object processed = processedRow.get(key);

            if (!Objects.equals(original, processed)) {
                logger.debug("Value transformation: {} -> {} for key '{}'",
                    original, processed, key);
            }
        }

        return processedRow;
    }
}
```

#### Step-by-Step Conversion Analysis

```java
@Component
public class ConversionDebugService {

    public DebugResult debugConversion(List<Map<String, Object>> data,
                                     Class<?> targetType,
                                     MappingConfig config) {
        DebugResult result = new DebugResult();

        // Step 1: Validate configuration
        try {
            MappingConfigLoader.validateHierarchy(config);
            result.addStep("Configuration validation: PASSED");
        } catch (Exception e) {
            result.addStep("Configuration validation: FAILED - " + e.getMessage());
            return result;
        }

        // Step 2: Analyze input data structure
        Set<String> inputFields = data.stream()
            .flatMap(row -> row.keySet().stream())
            .collect(Collectors.toSet());
        result.addStep("Input fields discovered: " + inputFields.size());
        result.addDetail("inputFields", inputFields);

        // Step 3: Test with diagnostic reporter
        List<String> warnings = new ArrayList<>();
        MappingConfig debugConfig = config.withReporter(Optional.of(warnings::add));

        try {
            List<JsonNode> jsonResults = converter.convertAll(data, JsonNode.class, debugConfig);
            result.addStep("Conversion to JsonNode: SUCCESS");
            result.addDetail("outputObjects", jsonResults.size());

            if (!warnings.isEmpty()) {
                result.addStep("Warnings generated: " + warnings.size());
                result.addDetail("warnings", warnings);
            }

            // Step 4: Analyze output structure
            if (!jsonResults.isEmpty()) {
                JsonNode firstResult = jsonResults.get(0);
                Set<String> outputFields = extractFieldNames(firstResult);
                result.addDetail("outputFields", outputFields);
                result.addDetail("sampleOutput", firstResult.toPrettyString());
            }

            // Step 5: Test POJO conversion
            List<?> pojoResults = converter.convertAll(data, targetType, debugConfig);
            result.addStep("POJO conversion: SUCCESS");

        } catch (Exception e) {
            result.addStep("Conversion failed: " + e.getMessage());
            result.addDetail("exception", e.getClass().getSimpleName());
            result.addDetail("exceptionMessage", e.getMessage());
        }

        return result;
    }

    private Set<String> extractFieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        extractFieldNamesRecursive(node, "", fields);
        return fields;
    }

    private void extractFieldNamesRecursive(JsonNode node, String prefix, Set<String> fields) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(fieldName -> {
                String fullPath = prefix.isEmpty() ? fieldName : prefix + "/" + fieldName;
                fields.add(fullPath);
                extractFieldNamesRecursive(node.get(fieldName), fullPath, fields);
            });
        }
    }
}
```

### Enhanced Logging Configuration

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="FLAT2POJO" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/flat2pojo.log</file>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/flat2pojo.%d{yyyy-MM-dd}.%i.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
    </appender>

    <!-- Separate appender for SPI warnings -->
    <appender name="SPI_WARNINGS" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/flat2pojo-warnings.log</file>
        <filter class="ch.qos.logback.core.filter.EvaluatorFilter">
            <evaluator>
                <expression>message.contains("conflict") || message.contains("Skipping")</expression>
            </evaluator>
            <onMismatch>DENY</onMismatch>
            <onMatch>ACCEPT</onMatch>
        </filter>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="io.github.pojotools.flat2pojo" level="INFO" additivity="false">
        <appender-ref ref="FLAT2POJO"/>
    </logger>

    <logger name="FLAT2POJO_WARNINGS" level="WARN" additivity="false">
        <appender-ref ref="SPI_WARNINGS"/>
    </logger>
</configuration>
```

### Production Monitoring Queries

**Prometheus Queries:**
```promql
# Conversion throughput
rate(flat2pojo_conversions_total[5m])

# Warning rate threshold alerting
rate(flat2pojo_warnings_total[5m]) / rate(flat2pojo_conversions_total[5m]) > 0.1

# Memory usage trending
process_resident_memory_bytes{job="flat2pojo-service"}

# Processing latency percentiles
histogram_quantile(0.95, rate(flat2pojo_processing_duration_seconds_bucket[5m]))
```

**Grafana Dashboard Panels:**
- Conversion volume over time
- Warning categories breakdown
- Processing latency heatmap
- Memory usage trends
- Top error messages table

## Enterprise Best Practices

### Configuration Management

**Version Control Strategy:**
```yaml
# Store mappings with versioning
# mappings/v1/user-mapping.yml
version: "1.0.0"
description: "User data conversion mapping"
separator: "/"
lists:
  - path: "users"
    keyPaths: ["users/id"]

# mappings/v2/user-mapping.yml
version: "2.0.0"
description: "Enhanced user mapping with preferences"
separator: "/"
lists:
  - path: "users"
    keyPaths: ["users/id"]
  - path: "users/preferences"
    keyPaths: ["users/preferences/category"]
```

**Environment-Specific Overrides:**
```java
@Configuration
@Profile("production")
public class ProductionMappingConfig {

    @Bean
    public MappingConfig userMappingConfig() {
        return MappingConfigLoader.fromResource("mappings/user-mapping.yml")
            .withNullPolicy(new NullPolicy(true))
            .withReporter(Optional.of(productionReporter()))
            .withValuePreprocessor(Optional.of(productionPreprocessor()));
    }
}
```

### Data Governance

**Schema Validation Integration:**
```java
public class ValidatingValuePreprocessor implements ValuePreprocessor {
    private final JsonSchema schema;

    @Override
    public Map<String, Object> process(Map<String, ?> row) {
        // Validate against JSON schema before processing
        ValidationResult validation = schema.validate(JsonNodeFactory.instance.objectNode()
            .setAll(row.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> JsonNodeFactory.instance.textNode(String.valueOf(entry.getValue()))
                ))));

        if (!validation.isSuccess()) {
            throw new DataValidationException("Schema validation failed: " + validation.getErrors());
        }

        return new HashMap<>(row);
    }
}
```

**Data Lineage Tracking:**
```java
public class LineageTrackingReporter implements Reporter {
    private final LineageService lineageService;

    @Override
    public void warn(String message) {
        if (message.contains("conflict")) {
            // Track data transformation conflicts for audit
            ConflictMetadata metadata = parseConflictMessage(message);
            lineageService.recordTransformation(
                metadata.getFieldPath(),
                metadata.getOldValue(),
                metadata.getNewValue(),
                "flat2pojo-conflict-resolution"
            );
        }
    }
}
```

### Security Considerations

**Sensitive Data Handling:**
```java
public class SecureValuePreprocessor implements ValuePreprocessor {
    private final Set<String> sensitiveFields = Set.of(
        "ssn", "creditCard", "password", "apiKey", "token"
    );

    @Override
    public Map<String, Object> process(Map<String, ?> row) {
        Map<String, Object> processed = new HashMap<>(row);

        processed.entrySet().removeIf(entry -> {
            String key = entry.getKey().toLowerCase();
            return sensitiveFields.stream().anyMatch(key::contains);
        });

        return processed;
    }
}
```

**Audit Compliance:**
```java
public class ComplianceAuditReporter implements Reporter {
    private final AuditLogger auditLogger;

    @Override
    public void warn(String message) {
        AuditEvent event = AuditEvent.builder()
            .timestamp(Instant.now())
            .service("flat2pojo")
            .action("data-transformation")
            .severity("WARN")
            .message(message)
            .userId(getCurrentUserId())
            .sessionId(getCurrentSessionId())
            .build();

        auditLogger.log(event);
    }
}
```

### Performance Tuning at Scale

**JVM Optimization:**
```bash
# Production JVM flags
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:+UseStringDeduplication
-XX:NewRatio=1
-Xms4g -Xmx8g
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/opt/dumps/
```

**Connection Pool Configuration:**
```java
@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource")
    public HikariConfig hikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(50);
        config.setMinimumIdle(10);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        return config;
    }
}
```

### Disaster Recovery

**Configuration Backup:**
```bash
#!/bin/bash
# backup-mappings.sh
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups/flat2pojo-configs/$DATE"

mkdir -p "$BACKUP_DIR"
cp -r src/main/resources/mappings/* "$BACKUP_DIR/"
tar -czf "/backups/flat2pojo-configs-$DATE.tar.gz" "$BACKUP_DIR"

# Upload to S3 or backup service
aws s3 cp "/backups/flat2pojo-configs-$DATE.tar.gz" s3://backup-bucket/flat2pojo/
```

**Graceful Degradation:**
```java
@Component
public class FallbackConversionService {
    private final Flat2Pojo primaryConverter;
    private final Flat2Pojo fallbackConverter;

    public <T> List<T> convertWithFallback(List<Map<String, Object>> data,
                                         Class<T> targetType,
                                         MappingConfig config) {
        try {
            return primaryConverter.convertAll(data, targetType, config);
        } catch (Exception primaryError) {
            logger.warn("Primary conversion failed, attempting fallback", primaryError);

            try {
                // Use simplified config for fallback
                MappingConfig fallbackConfig = config.withLists(Collections.emptyList());
                return fallbackConverter.convertAll(data, targetType, fallbackConfig);
            } catch (Exception fallbackError) {
                logger.error("Both primary and fallback conversion failed", fallbackError);
                throw new ConversionException("All conversion attempts failed", fallbackError);
            }
        }
    }
}
```

## Summary

This operations guide covers the essential aspects of deploying and maintaining flat2pojo in production environments:

- **Processing patterns** for optimal performance and resource utilization
- **Memory management** strategies with configuration caching and Jackson reuse
- **SPI-based monitoring** for comprehensive observability and quality control
- **Thread safety** considerations and recommended concurrency patterns
- **Production deployment** with circuit breakers, caching, and enterprise configuration
- **Advanced debugging** techniques using SPI diagnostics and step-by-step analysis
- **Enterprise best practices** for governance, security, and disaster recovery

The SPI interfaces (Reporter and ValuePreprocessor) provide powerful hooks for production monitoring, data quality control, and custom processing requirements. Combined with proper configuration management and monitoring, flat2pojo can reliably handle high-volume data transformation workloads in enterprise environments.
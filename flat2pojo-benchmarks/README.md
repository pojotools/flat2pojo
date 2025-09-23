# Flat2Pojo Performance Benchmarks

This module contains comprehensive JMH (Java Microbenchmark Harness) benchmarks for measuring the performance of the flat2pojo library across various scenarios and workloads.

## Overview

The benchmarks are designed to:
- Measure conversion performance across different data sizes and complexities
- Test path traversal optimization effectiveness
- Monitor memory allocation patterns
- Validate performance with realistic datasets
- Prevent performance regressions

## Benchmark Classes

### 1. CoreConversionBenchmark
Tests basic conversion operations:
- Simple flat map conversion
- Nested structure conversion
- Multiple row processing
- POJO materialization

### 2. PathTraversalBenchmark
Measures path manipulation performance:
- Path splitting operations
- Path hierarchy checks
- String manipulation optimizations
- Comparison with baseline Java operations

### 3. GroupingEngineBenchmark
Tests list grouping and array operations:
- List element upserts
- Array finalization
- Nested list processing
- Engine instantiation overhead

### 4. MemoryAllocationBenchmark
Monitors memory usage patterns:
- Small, medium, and large dataset processing
- Memory allocation profiling
- GC impact measurement
- Configuration caching efficiency

### 5. RealisticDatasetBenchmark
Real-world scenario testing:
- E-commerce data processing
- CRM system data handling
- Analytics event processing
- Mixed workload performance

### 6. ConfigurationBenchmark
Configuration loading and processing:
- YAML parsing performance
- Immutable configuration creation
- Hierarchy validation
- Derived field computation

### 7. PerformanceRegressionBenchmark
Comprehensive regression testing:
- Full dataset processing
- Complex nested structures
- Stream processing
- Configuration validation

## Running Benchmarks

### Build the Benchmark JAR
```bash
mvn clean package -pl flat2pojo-benchmarks
```

### Run All Benchmarks
```bash
java -jar flat2pojo-benchmarks/target/benchmarks.jar
```

### Run Specific Benchmark
```bash
java -jar flat2pojo-benchmarks/target/benchmarks.jar CoreConversion
```

### Run with Custom Parameters
```bash
java -jar flat2pojo-benchmarks/target/benchmarks.jar \
  -f 3 \
  -wi 5 \
  -i 10 \
  -t 2 \
  -prof gc
```

### Memory Profiling
```bash
java -jar flat2pojo-benchmarks/target/benchmarks.jar \
  MemoryAllocation \
  -prof gc \
  -prof stack
```

## Benchmark Parameters

- **Fork (-f)**: Number of JVM forks (default: 1-2)
- **Warmup Iterations (-wi)**: JVM warmup rounds (default: 2-5)
- **Measurement Iterations (-i)**: Actual measurement rounds (default: 3-10)
- **Threads (-t)**: Number of benchmark threads (default: 1)
- **Profilers (-prof)**: gc, stack, async, etc.

## Expected Performance Characteristics

### Core Conversion
- Simple maps: < 1µs per conversion
- Nested structures: < 5µs per conversion
- Batch processing: > 10,000 ops/sec

### Path Operations
- Path splitting: < 100ns for typical paths
- Hierarchy checks: < 50ns
- 10x faster than String.split() for deep paths

### Memory Usage
- Linear scaling with data size
- Minimal GC pressure for typical workloads
- Configuration caching reduces allocation overhead

### Realistic Workloads
- E-commerce: > 1,000 orders/sec
- CRM: > 500 complex records/sec
- Analytics: > 5,000 events/sec

## Continuous Performance Monitoring

Integrate these benchmarks into your CI/CD pipeline:

```bash
# Run core performance regression test
java -jar flat2pojo-benchmarks/target/benchmarks.jar \
  PerformanceRegression.regressionSubsetProcessing \
  -f 1 -wi 2 -i 3 \
  -rf json \
  -rff results.json

# Compare with baseline (implement in CI)
# Fail build if performance degrades > 20%
```

## Profiling Tips

1. **Memory Analysis**: Use `-prof gc` to monitor garbage collection
2. **Hotspot Analysis**: Use `-prof stack` to identify bottlenecks
3. **Allocation Profiling**: Use `-prof async:output=flamegraph`
4. **JIT Analysis**: Use `-jvmArgs -XX:+PrintCompilation`

## Benchmark Results Interpretation

- **Lower is better**: AverageTime, SampleTime modes
- **Higher is better**: Throughput mode
- **Watch for**: High variance, GC impact, memory leaks
- **Compare**: Before/after changes, different configurations

## Contributing

When adding new benchmarks:
1. Follow existing naming conventions
2. Include appropriate @Benchmark annotations
3. Use Blackhole.consume() for results
4. Add realistic test data
5. Document expected performance characteristics
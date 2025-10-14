# Flat2Pojo Performance Benchmarks

This module contains focused JMH (Java Microbenchmark Harness) benchmarks for measuring the performance of the flat2pojo library's main entry points.

## Overview

The benchmarks are designed to:
- **Test the main `Flat2PojoCore` entry points** (`convertAll()` and `convertOptional()`)
- **Measure performance with realistic data volumes**: 2,500, 5,000, and 10,000 records
- **Cover common conversion scenarios**: simple flat structures, nested lists with grouping, and complex multi-level lists
- **Prevent performance regressions** in the core conversion pipeline

## Benchmark Focus

### Flat2PojoCoreBenchmark
Tests the main conversion entry points with three realistic scenarios:

1. **Simple Flat Structure**
   - Nested paths without lists (e.g., `user/profile/age`)
   - Tests basic path traversal and object building
   - Data volumes: 2.5k, 5k, 10k records

2. **Nested List with Grouping**
   - Single-level lists requiring grouping (orders with items)
   - Tests list deduplication and sorting
   - 5 items per order group
   - Data volumes: 2.5k, 5k, 10k records

3. **Complex Multi-Level Lists**
   - Nested lists with multiple levels (customers → orders → items)
   - Tests hierarchical list processing and array management
   - 3 orders per customer, 5 items per order (15 rows per customer)
   - Data volumes: 2.5k, 5k, 10k records

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
java -jar flat2pojo-benchmarks/target/benchmarks.jar Flat2PojoCoreBenchmark
```

### Run Specific Test Pattern
```bash
# Run only 10k record benchmarks
java -jar flat2pojo-benchmarks/target/benchmarks.jar ".*10000"

# Run only complex scenario benchmarks
java -jar flat2pojo-benchmarks/target/benchmarks.jar ".*Complex.*"

# Run only simple scenarios at all volumes
java -jar flat2pojo-benchmarks/target/benchmarks.jar ".*Simple.*"
```

### Run with Custom Parameters
```bash
java -jar flat2pojo-benchmarks/target/benchmarks.jar \
  -f 2 \
  -wi 3 \
  -i 5 \
  -t 2
```

### Memory Profiling
```bash
java -jar flat2pojo-benchmarks/target/benchmarks.jar \
  Flat2PojoCoreBenchmark \
  -prof gc \
  -prof stack
```

## Benchmark Parameters

- **Mode**: Throughput (operations per second)
- **Warmup**: 3 iterations, 2 seconds each
- **Measurement**: 5 iterations, 3 seconds each
- **Forks**: 1 JVM fork with 1 warmup fork
- **Output**: Operations per second

## Expected Results

Performance targets (ops/sec on modern hardware):
- **Simple 10k**: > 50 ops/sec
- **NestedList 10k**: > 30 ops/sec
- **Complex 10k**: > 20 ops/sec

Lower volumes (2.5k, 5k) should scale proportionally.

## Interpreting Results

The benchmark output shows:
1. **Throughput**: How many conversions per second
2. **Score**: Average operations per second
3. **Error margin**: Statistical confidence interval
4. **Units**: ops/s (operations per second)

Example output:
```
Benchmark                                    Mode  Cnt   Score   Error  Units
Flat2PojoCoreBenchmark.convertAll_Simple_2500  thrpt    5  120.5 ± 3.2  ops/s
Flat2PojoCoreBenchmark.convertAll_Simple_5000  thrpt    5   65.3 ± 2.1  ops/s
Flat2PojoCoreBenchmark.convertAll_Simple_10000 thrpt    5   35.7 ± 1.5  ops/s
```

## Continuous Integration

Benchmarks should be run:
- Before major releases
- After significant performance-related changes
- To validate optimizations
- To catch performance regressions

Store baseline results for comparison across versions.

## Notes

- Benchmarks run in throughput mode to measure sustained performance
- Each test uses pre-generated datasets to avoid measurement bias
- GC profiling can be enabled to understand memory pressure
- Results may vary based on hardware and JVM settings

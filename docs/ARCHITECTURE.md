# Architecture & Design Decisions

For detailed architecture documentation, algorithm flow, and component design, see **[PSEUDOCODE.md](PSEUDOCODE.md)**.

## Overview

flat2pojo uses a **Jackson-first architecture** with a multi-stage processing pipeline:

1. **Grouping Layer** - Groups flat rows by root keys
2. **Processing Layer** - Transforms flat maps to JSON trees
3. **Array Management** - Deduplication, sorting, and finalization
4. **Materialization Layer** - Converts JSON to POJOs via Jackson

## Key Design Decisions

### Why Jackson-First?

Building a JSON tree first (via Jackson's ObjectNode) provides:
- Type safety - JsonNode types match POJO field types
- Compatibility - Existing Jackson annotations and deserializers work seamlessly
- Simplicity - Let Jackson handle all type conversion edge cases
- Testability - Can verify intermediate JSON structure before materialization

See [PSEUDOCODE.md#design-rationale](PSEUDOCODE.md#design-rationale) for complete rationale.

### Why Hierarchical Processing?

Processing lists in declaration order enables:
- Nested lists - Child lists inserted into parent elements via cache
- Early validation - Catch missing parent declarations at startup
- Cache efficiency - Parent element available when processing child in same row
- Determinism - Predictable processing order, no hidden dependencies

See [PSEUDOCODE.md#why-hierarchical-processing](PSEUDOCODE.md#why-hierarchical-processing) for details.

### Why IdentityHashMap for Buckets?

- ArrayNode identity uniquely identifies each array in the tree
- Avoids expensive `.equals()` calls on large ArrayNode instances
- Matches natural tree structure (one bucket per physical array node)

See [PSEUDOCODE.md#why-identityhashmap-for-buckets](PSEUDOCODE.md#why-identityhashmap-for-buckets) for details.

## Component Architecture

### Array Management Components

flat2pojo uses separate, focused managers for different array types:

#### Non-Primitive Arrays (Object Collections)

**ArrayManager** - Coordinates object array lifecycle
- Delegates to specialized components for each concern
- Manages bucket and comparator state via IdentityHashMap
- Handles upsert and finalization

**Supporting Components:**
- `ArrayNodeResolver` - Resolves and creates array nodes within the object tree
- `CompositeKeyExtractor` - Extracts composite keys from row values for deduplication
- `ArrayBucket` - Manages element accumulation and deduplication via composite keys
- `ArrayFinalizer` - Applies sorting and writes ordered elements to arrays
- `ComparatorBuilder` - Precomputes comparators from configuration

**Key Design:**
- `ArrayBucket` uses `LinkedHashMap` to maintain insertion order while deduplicating by composite key
- Sorting is lazy - elements accumulated, then sorted once during finalization
- Cache invalidation ensures consistency when new elements added

#### Primitive Arrays (Value Collections)

**PrimitiveArrayManager** - Coordinates primitive array lifecycle
- Mirrors ArrayManager architecture for consistency
- Optimized for primitive value accumulation across rows

**Supporting Components:**
- `PrimitiveArrayRuleCache` - Fast O(1) lookup for primitive list rules
- `PrimitiveArrayNodeFactory` - Creates and attaches array nodes to the tree
- `PrimitiveArrayBucket` - Accumulates primitive values with deduplication support
- `PrimitiveArrayFinalizer` - Batch processes and sorts accumulated values

**Performance Optimization:**
- Uses accumulation + sort-at-end pattern for sorted lists: O(P + V log V) instead of O(P × V)
- Insertion-order lists use immediate append for optimal memory efficiency
- Deduplication uses HashSet for O(1) duplicate detection

#### Naming Convention

| Prefix | Purpose | Examples |
|--------|---------|----------|
| `Primitive*` | Primitive type arrays | `PrimitiveArrayManager`, `PrimitiveArrayBucket` |
| `Array*` | Object arrays | `ArrayManager`, `ArrayBucket` |
| `List*` | Domain concepts | `ListRuleProcessor`, `ListHierarchyCache` |

This clear separation ensures:
- Array **structures** use "Array" suffix
- Domain **logic** uses "List" prefix
- No ambiguity about class responsibilities

### Architecture Symmetry

Both primitive and non-primitive array processing follow identical patterns:

```
PrimitiveArrayManager              ArrayManager
  ├─ PrimitiveArrayRuleCache         ├─ ArrayNodeResolver
  ├─ PrimitiveArrayNodeFactory       ├─ CompositeKeyExtractor
  ├─ PrimitiveArrayBucket            ├─ ArrayBucket
  └─ PrimitiveArrayFinalizer         └─ ArrayFinalizer
```

Benefits:
- **Consistency** - Same delegation style across all array types
- **Maintainability** - Learn one pattern, understand both
- **Extensibility** - Easy to add new array types following the pattern

### Single Responsibility Principle

Each component has one clear purpose:

**Managers** (Coordinators)
- Orchestrate component lifecycle
- Route operations to appropriate helpers
- Manage state and caching

**Resolvers/Factories** (Creation)
- Node creation and attachment
- Path traversal and resolution

**Buckets** (Accumulation)
- Element/value collection
- Deduplication logic
- Insertion order maintenance

**Finalizers** (Completion)
- Batch processing
- Sorting application
- Tree traversal and cleanup

**Extractors/Builders** (Support)
- Key extraction from rows
- Comparator construction
- Rule caching and lookup

## Clean Code Standards

The refactored architecture adheres to strict clean code guidelines:

### Method Characteristics
- **≤4 parameters** - Complex parameter lists use context objects
- **≤1 indent level** - Guard clauses and early returns
- **~4-6 lines per method** - Small, focused functions
- **Positive conditionals** - `if (isValid())` not `if (!isInvalid())`

### Class Characteristics
- **Single Responsibility** - One reason to change
- **Dependency Injection** - Constructor injection over `new`
- **Intention-revealing names** - Clear, descriptive names
- **No flag arguments** - Separate methods instead of boolean flags

### Example: Parameter Reduction

```java
// Before: 5 parameters (violation)
private void addImmediately(
    String cacheKey, Path path, JsonNode value, 
    ObjectNode targetRoot, PrimitiveListRule rule) { ... }

// After: 1 parameter using context object
private record AddContext(
    String cacheKey, Path path, JsonNode value,
    ObjectNode targetRoot, PrimitiveListRule rule) {}

private void addImmediately(AddContext context) { ... }
```

## Component Diagram

For complete component design, interaction diagrams, and data flow:

- [PSEUDOCODE.md#component-diagram](PSEUDOCODE.md#component-diagram)
- [PSEUDOCODE.md#sequence-diagram](PSEUDOCODE.md#sequence-diagram)
- [PSEUDOCODE.md#key-collaborators](PSEUDOCODE.md#key-collaborators)

## Performance Characteristics

### Complexity Analysis

**Non-Primitive Arrays:**
- Upsert: O(1) average for LinkedHashMap operations
- Sorting: O(K log K) where K = unique elements per array
- Finalization: O(A × K log K) where A = number of arrays

**Primitive Arrays:**
- Immediate insertion (insertion-order): O(1) per value
- Accumulation (sorted): O(1) per value, O(V log V) at finalization
- Overall sorted: O(P + V log V) vs. naive O(P × V)

For complete performance analysis and optimization strategies:

- [PSEUDOCODE.md#performance-characteristics](PSEUDOCODE.md#performance-characteristics)
- [OPERATIONS.md#performance-optimization](OPERATIONS.md#performance-optimization)

## Related Documentation

- [PSEUDOCODE.md](PSEUDOCODE.md) - Complete algorithm flow and component design
- [OPERATIONS.md](OPERATIONS.md) - API reference and production operations
- [MAPPINGS.md](MAPPINGS.md) - Configuration DSL specification
- [CHANGELOG.md](CHANGELOG.md) - Recent architectural changes and improvements
- [README.md](README.md) - Project overview and quick start

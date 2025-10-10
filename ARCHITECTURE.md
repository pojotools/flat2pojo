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

For complete component design, interaction diagrams, and data flow:

- [PSEUDOCODE.md#component-diagram](PSEUDOCODE.md#component-diagram)
- [PSEUDOCODE.md#sequence-diagram](PSEUDOCODE.md#sequence-diagram)
- [PSEUDOCODE.md#key-collaborators](PSEUDOCODE.md#key-collaborators)

## Performance Characteristics

For performance analysis, complexity analysis, and optimization strategies:

- [PSEUDOCODE.md#performance-characteristics](PSEUDOCODE.md#performance-characteristics)
- [OPERATIONS.md#performance-optimization](OPERATIONS.md#performance-optimization)

## Related Documentation

- [PSEUDOCODE.md](PSEUDOCODE.md) - Complete algorithm flow and component design
- [OPERATIONS.md](OPERATIONS.md) - API reference and production operations
- [MAPPINGS.md](MAPPINGS.md) - Configuration DSL specification
- [README.md](README.md) - Project overview and quick start

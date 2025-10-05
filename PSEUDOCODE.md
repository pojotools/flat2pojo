# FLAT2POJO: Converting Flat Key-Value Maps to Nested POJOs

## HIGH-LEVEL OVERVIEW

**Input:**  List of flat Maps (e.g., `{"person/name": "Alice", "person/age": 30}`)
**Output:** List of POJOs (e.g., `Person{name="Alice", age=30}`)

## MAIN ALGORITHM: convertAll(rows, targetType, config)

```
1. VALIDATE configuration hierarchy
    - Ensure parent lists declared before children
    - Ensure implied parent lists exist

2. GROUP rows by rootKeys
    - If rootKeys defined: group rows with same root key values
    - If no rootKeys: treat all rows as single group
    - Example: Group all rows where "orderId"=123 together

3. FOR EACH group:
   a. processRootGroup → returns one POJO instance

4. RETURN list of POJOs
```

## DETAILED: processRootGroup(groupRows, type, config)

```
Initialize:
- root = empty JSON ObjectNode
- GroupingEngine (manages arrays with deduplication & sorting)
- FlatTreeBuilder (applies value transformations)

FOR EACH row in groupRows:
  processRowIntoTree(row, root, engines, config)

finalizeArrays(root) → sort and dedupe all arrays
materializeResult(root, type) → convert JSON to POJO using Jackson
```

## DETAILED: processRowIntoTree(row, root, engines, config)

### PHASE 1: Value Preprocessing (Optional SPI)
```
IF valuePreprocessor configured:
  row = valuePreprocessor.process(row)
  // Example: normalize phone numbers, convert YES/NO → boolean
```

### PHASE 2: Value Transformation
```
rowNode = FlatTreeBuilder.buildTreeForRow(row)
  - Convert flat map to nested tree
  - Apply primitive splits: "tags": "a,b,c" → ["a", "b", "c"]
  - Apply blanksAsNulls: "" → null
  - Convert to JsonNode types

rowValues = flatten(rowNode)
  - Back to path→value map: {"person/name": JsonNode("Alice")}
  - Why? List processing needs paths to extract composite keys
```

### PHASE 3: Process List Rules (Hierarchically)
```
skippedPaths = {}
listElementCache = {}  // Maps listPath → current element for this row

FOR EACH list rule (in declaration order):

    // Skip if parent list was skipped
    IF rule.path is under any skippedPath:
      SKIP and warn

    // Find where to attach this list
    parentListPath = findParentListPath(rule.path)
    IF parentListPath exists:
      baseObject = listElementCache[parentListPath]
      relativePath = rule.path MINUS parentListPath
    ELSE:
      baseObject = root
      relativePath = rule.path

    // Extract composite key from keyPaths
    keyValues = []
    FOR EACH keyPath in rule.keyPaths:
      value = rowValues[keyPath]
      IF value is null or missing:
        SKIP this list entry entirely
        Add to skippedPaths
        WARN user
        CONTINUE to next rule
      keyValues.append(value)

    compositeKey = CompositeKey(keyValues)

    // Upsert into array
    element = GroupingEngine.upsertListElement(
      baseObject,
      relativePath,
      compositeKey,
      rule.dedupe,
      rule.onConflict
    )

    IF element created:
      listElementCache[rule.path] = element

      // Copy values under this list path into the element
      copyListSubtreeValues(rowValues, element, rule)
        FOR EACH path→value in rowValues:
          IF path is under rule.path:
            AND path is NOT in a child list subtree:
              suffix = path MINUS rule.path
              writeValue(element, suffix, value, rule.onConflict)
```

### PHASE 4: Process Non-List Values
```
processNonListValues(rowValues, root, skippedPaths)

FOR EACH path→value in rowValues:
  IF path is NOT a list path:
    AND path is NOT under any skipped list:
      writeValue(root, path, value)
```

## KEY COMPONENTS

### GroupingEngine: Manages list/array construction

```
State per array:
- ArrayBucket: Map<CompositeKey, ObjectNode>
    - Deduplicates elements by composite key
    - Handles conflicts (error, firstWins, lastWins, merge)
- Comparators: Sort specifications from orderBy rules

upsertListElement(base, path, key, dedupePolicy):
1. Navigate to parent object via path
2. Get or create array field
3. Get bucket for this array (or create new)
4. bucket.upsert(key, newElement, conflictPolicy)
   - If key exists & dedupe=true: handle conflict
   - If key exists & dedupe=false: add anyway
   - Else: add new element
5. Return the element

finalizeArrays(root):
Traverse tree depth-first
FOR EACH array with bucket:
  1. Get elements from bucket
  2. Sort by orderBy comparators
  3. Replace array contents with sorted elements
```

### FlatTreeBuilder: Value transformations

```
buildTreeForRow(row):
root = {}
FOR EACH key→value in row:
  1. Split key by separator: "person/address/city" → ["person", "address", "city"]
  2. Navigate/create path: root.person.address
  3. Handle primitive splits if configured: "a,b,c" → ["a", "b", "c"]
  4. Apply blanksAsNulls: "" → null
  5. Set leaf value: root.person.address.city = value
```

### ConflictHandler: Resolves field conflicts

```
writeScalarWithPolicy(target, field, newValue, policy):
existing = target[field]

IF existing is null:
  target[field] = newValue
  RETURN

SWITCH policy:
  error:        THROW exception
  firstWins:    IGNORE newValue, KEEP existing
  lastWins:     REPLACE with newValue
  merge:
    IF both are objects: deepMerge(existing, newValue)
    ELSE: fallback to lastWins
```

### ConfigurationCache: Pre-computed lookups (O(1) access)

```
buildCaches(config):
  allListPaths = {}        // Contains list paths + all their prefixes
  parentListPaths = {}     // Maps child list → parent list
  seenListPaths = {}

  FOR EACH list rule:
    path = rule.path
    allListPaths.add(path)

    // Build all prefixes
    segments = split(path, separator)
    FOR EACH prefix in segments[0..n-1]:
      allListPaths.add(prefix)

      // Track parent (deepest prefix that's a list)
      IF prefix in seenListPaths:
        parent = prefix

    IF parent exists:
      parentListPaths[path] = parent

    seenListPaths[path] = path

isListPath(path):
  // O(k) where k = path segments
  IF path in allListPaths:
    RETURN true

  FOR EACH prefix of path:
    IF prefix in allListPaths:
      RETURN true

  RETURN false
```

### PathCache: Per-row segment caching

```
PathCache:
  segmentCache = {}  // Maps path → List<String> segments

  getSegments(path):
    IF path not in cache:
      cache[path] = PathOps.splitPath(path, separator)
    RETURN cache[path]

  // Avoids repeated path splitting (4+ times per value)
  // Lives for one row only, then discarded
```

## PERFORMANCE CHARACTERISTICS

### Time Complexity
- **Overall:** O(n × m) where n = rows, m = average values per row
- **List path check:** O(k) where k = path segments (via prefix cache)
- **Parent lookup:** O(1) (pre-computed in ConfigurationCache)
- **Path splitting:** O(k) amortized via PathCache

### Space Complexity
- **ConfigurationCache:** O(L × d) where L = list rules, d = avg path depth
- **PathCache:** O(m × k) per row, where m = unique paths, k = segments
- **ArrayBuckets:** O(unique elements) across all lists

### Key Optimizations
1. **Prefix caching** - `allListPaths` stores all prefixes for O(1) lookup
2. **Path segment cache** - Avoids repeated `splitPath()` calls per row
3. **Single-pass cache build** - Computes parent relationships while building prefixes
4. **IdentityHashMap** - Array buckets use identity to avoid `.equals()` overhead

## CORRECTNESS GUARANTEES

### Hierarchical Processing
Lists processed in **declaration order** ensures:
- Parent lists populated before children
- Child elements inserted into correct parent element
- Validation catches missing parent declarations

### Deduplication
Composite keys ensure:
- Elements with same key values deduplicated per array
- Conflict policies control merge behavior
- Works across multiple rows

### Data Integrity
- Missing keyPath values → skip list element + warn via Reporter
- Skipped parents → skip all descendants
- Conflict policies prevent silent data loss

## EXAMPLE EXECUTION

```
Input Rows:
  1. {order/id: "123", order/items/id: "A", order/items/name: "Widget"}
  2. {order/id: "123", order/items/id: "B", order/items/name: "Gadget"}

Config:
  lists:
    - path: "order/items"
      keyPaths: ["order/items/id"]

Execution:
  Group: All rows (no rootKeys)

  Row 1:
    - rowValues = {"order/id": "123", "order/items/id": "A", ...}
    - Process list "order/items":
      - key = ["A"]
      - Upsert into root.order.items array
      - element = {id: "A", name: "Widget"}
    - Process non-list:
      - root.order.id = "123"

  Row 2:
    - Process list "order/items":
      - key = ["B"]
      - Upsert into root.order.items array
      - element = {id: "B", name: "Gadget"}
    - Process non-list:
      - root.order.id = "123" (conflict handled)

  Finalize:
    - Sort arrays per orderBy rules

  Result:
    Order {
      id: "123",
      items: [
        {id: "A", name: "Widget"},
        {id: "B", name: "Gadget"}
      ]
    }
```

## DESIGN RATIONALE

### Why Build-Then-Flatten?

The `processRowValues()` method builds a tree then flattens it. Why?

**Reason:** `FlatTreeBuilder` handles complex value transformations:
- Primitive splits: `"a,b,c"` → `["a", "b", "c"]` (creates ArrayNode)
- Type conversions: String → proper JsonNode types
- BlanksAsNulls: `""` → `null`

These transformations require Jackson's type system. The tree is then flattened because:
- List processing needs **paths** to extract composite keys
- Values must be routed to correct list elements based on hierarchical structure
- Simpler to work with path→value map than nested tree traversal

### Why Jackson-First?

Building a JSON tree first (via Jackson's ObjectNode) provides:
- **Type safety** - JsonNode types match POJO types
- **Compatibility** - Existing Jackson annotations, deserializers work
- **Simplicity** - Let Jackson handle all type conversion edge cases
- **Testability** - Can verify intermediate JSON structure

### Why Hierarchical Processing?

Processing lists in declaration order enables:
- **Nested lists** - Child lists inserted into parent elements
- **Validation** - Catch missing parent declarations early
- **Cache efficiency** - Parent element available when processing child
- **Determinism** - Predictable processing order

## SUMMARY

flat2pojo converts flat maps to nested POJOs through:

1. **Grouping** - Rows → root objects
2. **Transformation** - Values → JsonNodes
3. **Hierarchical upsert** - Lists with deduplication
4. **Finalization** - Sort + convert to POJO

Key characteristics:
- **O(n) processing** with optimized path lookups
- **Configurable** via declarative YAML
- **Extensible** via SPI (Reporter, ValuePreprocessor)
- **Robust** with conflict resolution and validation

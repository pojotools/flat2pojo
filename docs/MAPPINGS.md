# Mapping Configuration Guide

This document provides detailed information about flat2pojo's mapping configuration system, including configuration schema, semantic rules, hierarchical grouping, conflict resolution, and extensibility options.

## Configuration Schema

The `MappingConfig` object defines all mapping behavior:

```yaml
separator: "/"                    # Path segment delimiter (default: "/")
rootKeys: []                      # Keys for grouping rows (empty = single group)

lists:                            # List rules (processed in declaration order)
  - path: "definitions/modules"   # Absolute path to list
    keyPaths: ["id"]              # Relative paths for a composite key
    orderBy:                      # Sort specifications
      - path: "name"              # Relative path
        direction: asc|desc
        nulls: first|last
    dedupe: true                  # Enable deduplication
    onConflict: firstWriteWins    # error | firstWriteWins | lastWriteWins | merge

primitives:                       # String-to-array split rules
  - path: "tags"                  # Absolute path
    delimiter: ","                # Split delimiter
    trim: true                    # Trim whitespace from elements

nullPolicy:
  blanksAsNulls: true             # Treat blank strings as null

reporter: Optional<Reporter>      # SPI for warnings/errors
valuePreprocessor: Optional<ValuePreprocessor>  # SPI for row transformation
```

## Configuration Semantics

### Path Resolution Rules

1. **Relative vs Absolute Paths:**
   - List rules use **absolute paths** (e.g., `"definitions/modules"`)
   - `keyPaths` within list rules use **relative paths** (e.g., `"id"` not `"definitions/modules/id"`)
   - `orderBy` paths within list rules use **relative paths** (e.g., `"name"` not `"definitions/modules/name"`)
   - Validation enforces these constraints

2. **Declaration Order Matters:**
   - Lists must be declared **parent-before-child**
   - Processing follows declaration order
   - Enables nested list elements to reference parent elements from the same row

3. **Separator Semantics:**
   - Single or multi-character separators supported
   - Used for all path operations (split, join, traversal)
   - Single-character separators provide best performance

### Deduplication Behavior

**First-Write-Wins Semantics:**
- `ArrayBucket` implements first-write-wins for existing composite keys
- First row with a composite key establishes the list element
- Subsequent rows with same composite key **reuse** the existing element
- Element population continues across multiple rows in the same group
- This enables multi-row population of complex list elements

**Example:**
```
Row 1: items/id=A, items/name=Widget
Row 2: items/id=A, items/qty=10
Result: [{id: "A", name: "Widget", qty: 10}]  // Single element, merged from both rows
```

### Conflict Resolution Policies

Applied when writing values to existing fields:

| Policy           | Behavior                              | When to Use                                    |
|------------------|---------------------------------------|------------------------------------------------|
| `error`          | Throw exception on scalar conflicts   | Strict validation, detect data inconsistencies |
| `firstWriteWins` | Keep existing value, ignore new value | Stable defaults, preserve initial values       |
| `lastWriteWins`  | Overwrite with new value              | Override behavior, use most recent data        |
| `merge`          | Deep merge objects, overwrite scalars | Flexible merging, combine nested structures    |

**Merge Policy Details:**
- Objects are recursively merged (fields combined)
- Scalars are overwritten (last-write-wins fallback)
- Arrays are replaced (no element-level merging)

## Root Keys

Root keys control how flat rows are grouped into separate root-level objects. By default, all rows contribute to a single result object. With root keys, you can partition data into multiple objects based on key field values.

### Basic Root Key Usage

```yaml
rootKeys: ["organizationId"]
lists:
  - path: "departments"
    keyPaths: ["id"]
```

**Input Data:**
```
organizationId=acme, name=ACME Corp, departments/id=eng, departments/name=Engineering
organizationId=acme, name=ACME Corp, departments/id=sales, departments/name=Sales
organizationId=beta, name=Beta LLC, departments/id=dev, departments/name=Development
```

**Output (3 rows → 2 objects):**
```json
[
  {
    "organizationId": "acme",
    "name": "ACME Corp",
    "departments": [
      {"id": "eng", "name": "Engineering"},
      {"id": "sales", "name": "Sales"}
    ]
  },
  {
    "organizationId": "beta",
    "name": "Beta LLC",
    "departments": [
      {"id": "dev", "name": "Development"}
    ]
  }
]
```

### Compound Root Keys

Use multiple fields to create compound grouping keys:

```yaml
rootKeys: ["region", "organizationId"]
```

**Behavior:**
- Objects are grouped by the combination of ALL root key values
- Order of root keys matters for consistent results

### Handling Missing Root Keys

When a root key field is missing or has a null value in a row:

**Behavior:**
- The row is grouped under a `null` key value
- All rows with missing/null root keys are grouped together
- Processing continues normally - no warnings are logged
- The resulting object will have `null` or missing fields for those root keys

**Example:**
```yaml
rootKeys: ["organizationId"]
```

**Input (3 rows):**
```
organizationId=acme, name=ACME Corp, departments/id=eng
organizationId=acme, name=ACME Corp, departments/id=sales
name=Unnamed Org, departments/id=dev    # Missing organizationId
```

**Output (2 objects):**
```json
[
  {
    "organizationId": "acme",
    "name": "ACME Corp",
    "departments": [
      {"id": "eng"},
      {"id": "sales"}
    ]
  },
  {
    "organizationId": null,              // Missing key treated as null
    "name": "Unnamed Org",
    "departments": [
      {"id": "dev"}
    ]
  }
]
```

### Root Keys vs List Rules

Root keys and list rules are **complementary features** that work together in a two-phase process:

**Phase 1: Root Key Grouping (Optional)**
- When `rootKeys` is configured, rows are partitioned into groups based on root key values
- Each unique combination of root key values creates one output object
- Without `rootKeys`, all rows form a single group, producing one output object

**Phase 2: List Processing (Per Group)**
- Within each group, list rules create nested arrays
- Each group is processed independently with its own list state
- List rules use `keyPaths` to deduplicate and merge list elements within that group

**Processing Order:**
```
Input Rows
    ↓
[RootKeyGrouper] ← Partitions by rootKeys (if configured)
    ↓
Group 1, Group 2, ..., Group N
    ↓
[GroupingEngine] ← Processes each group independently
    ↓              - Applies list rules
    ↓              - Creates arrays via keyPaths
    ↓
Object 1, Object 2, ..., Object N
```

**Example:** The "Basic Root Key Usage" example above demonstrates both features:
- Root keys partition 3 rows into 2 groups (acme, beta)
- Within each group, the `departments` list rule creates the nested `departments` array
- Result: 2 objects, each with its own `departments` list

## List Rules

List rules define how flat data is grouped into lists and nested structures.

### Basic Structure

```yaml
lists:
  - path: "tasks"                    # Target path for the list
    keyPaths: ["id"]                 # Fields that uniquely identify list elements (relative)
    orderBy: []                      # Optional sorting rules
    dedupe: true                     # Remove duplicate elements (default: true)
    onConflict: lastWriteWins        # How to handle field conflicts
```

### Hierarchical Grouping

Lists can be nested to create complex hierarchical structures. **Parent lists must be declared before child lists.**

```yaml
lists:
  # Parent list - must come first
  - path: "departments"
    keyPaths: ["id"]

  # Child list - references parent
  - path: "departments/employees"
    keyPaths: ["id"]
    orderBy:
      - path: "name"
        direction: asc
```

**Input Data:**
```
department/id=1, department/name=Engineering, department/employees/id=101, department/employees/name=Alice
department/id=1, department/name=Engineering, department/employees/id=102, department/employees/name=Bob
```

**Output Structure:**
```json
{
  "departments": [
    {
      "id": "1",
      "name": "Engineering",
      "employees": [
        {"id": "101", "name": "Alice"},
        {"id": "102", "name": "Bob"}
      ]
    }
  ]
}
```

### KeyPaths

KeyPaths define the composite key that uniquely identifies list elements. Multiple keyPaths create compound keys.

```yaml
lists:
  - path: "events"
    keyPaths: ["date", "location"]  # Compound key (relative paths)
```

**Behavior:**
- Elements with the same composite key are merged
- Order of keyPaths matters for consistent results
- Use relative paths from the list element (not absolute paths from root)

### Handling Missing KeyPaths

When any keyPath field is missing or has a null value in a row:

**Behavior:**
- The entire list processing for that row is skipped
- The list path is marked as "skipped" for that row
- Any child lists under that path are also skipped
- A warning is logged via Reporter: `"Skipping list rule '<path>' because keyPath(s) <keyPaths> are missing or null"`
- Row values for that list path are NOT written to the object
- Processing continues with the next row

**Example:**
```yaml
lists:
  - path: "departments"
    keyPaths: ["id"]
  - path: "departments/employees"
    keyPaths: ["id"]
```

**Input (3 rows):**
```
departments/id=eng, departments/name=Engineering, departments/employees/id=101, departments/employees/name=Alice
departments/id=sales, departments/name=Sales, departments/employees/id=201, departments/employees/name=Bob
departments/name=Unnamed, departments/employees/id=301, departments/employees/name=Charlie  # Missing departments/id
```

**Output:**
```json
{
  "departments": [
    {
      "id": "eng",
      "name": "Engineering",
      "employees": [{"id": "101", "name": "Alice"}]
    },
    {
      "id": "sales",
      "name": "Sales",
      "employees": [{"id": "201", "name": "Bob"}]
    }
  ]
}
```

**What happened:**
- Row 3 has missing `departments/id` keyPath
- Row 3 list processing is skipped (warning logged)
- Charlie's employee record is NOT added (parent department skipped)
- "Unnamed" department is NOT added to the output

**Important:** This is the **default and only behavior**. Missing keyPaths always cause list processing to be skipped for that row. There is no configuration option to change this behavior.

### OrderBy Rules

Sort list elements by one or more fields:

```yaml
lists:
  - path: "tasks"
    keyPaths: ["id"]
    orderBy:
      - path: "priority"            # Primary sort (relative)
        direction: desc             # desc or asc
        nulls: last                 # first or last
      - path: "created"             # Secondary sort (relative)
        direction: asc
        nulls: first
```

**Sort Behavior:**
- Multiple orderBy rules create multi-level sorting
- Numeric values are compared numerically
- String values are compared lexicographically
- Null handling is configurable per field

### Conflict Policies

When multiple rows contribute to the same list element, conflicts may arise. The `onConflict` policy determines resolution:

| Policy | Behavior | Use Case |
|--------|----------|----------|
| `error` | Throw exception on scalar conflicts | Strict validation |
| `firstWriteWins` | Keep the first encountered value | Stable defaults |
| `lastWriteWins` | Use the most recent value | Override behavior |
| `merge` | Deep merge objects, overwrite scalars | Flexible merging |

**Example:**

```yaml
lists:
  - path: "users"
    keyPaths: ["id"]
    onConflict: merge
```

**Input with conflict:**
```
users/id=1, users/name=John, users/email=john@old.com
users/id=1, users/name=John, users/email=john@new.com, users/profile/bio=Developer
```

**Result with `merge` policy:**
```json
{
  "users": [
    {
      "id": "1",
      "name": "John",
      "email": "john@new.com",     // Last write wins for scalars
      "profile": {
        "bio": "Developer"        // Objects are merged
      }
    }
  ]
}
```

## Primitive Split Rules

Transform delimited strings into arrays:

```yaml
primitives:
  - path: "tags"
    split:
      delimiter: ","
      trim: true
  - path: "coordinates"
    split:
      delimiter: "|"
      trim: false
```

**Input:**
```
tags=java, spring, microservices
coordinates=40.7128|-74.0060
```

**Output:**
```json
{
  "tags": ["java", "spring", "microservices"],
  "coordinates": ["40.7128", "-74.0060"]
}
```

**Split Options:**
- `delimiter`: String to split on (can be multiple characters)
- `trim`: Whether to trim whitespace from each part
- Empty parts are preserved (e.g., `"a,,b"` → `["a", "", "b"]`)
- Respects `nullPolicy.blanksAsNulls` for empty parts

## Null Policy

Control how blank/empty values are handled:

```yaml
nullPolicy:
  blanksAsNulls: true
```

**Effect:**
- `blanksAsNulls: true`: Empty strings become `null` in JSON
- `blanksAsNulls: false`: Empty strings remain as `""`
- Applies to both regular fields and split array elements

## Path Conventions

### Separators

```yaml
separator: "/"  # Default - can be changed to any string
```

**Examples:**
- `separator: "/"` → `department/employees/name`
- `separator: "."` → `department.employees.name`
- `separator: "__"` → `department__employees__name`

### Absolute vs Relative Paths

- **List paths**: Absolute paths from root
- **KeyPaths**: Relative to the list element
- **OrderBy paths**: Relative to the list element

```yaml
lists:
  - path: "departments"                    # Absolute
    keyPaths: ["id"]                       # Relative to departments

  - path: "departments/employees"          # Absolute (nested)
    keyPaths: ["id"]                       # Relative to departments/employees
    orderBy:
      - path: "name"                       # Relative to employees element
```

## Validation Rules

flat2pojo enforces several validation rules to ensure consistent behavior:

### 1. Parent-Child Declaration Order

Child lists must be declared **after** their parent lists:

```yaml
# ✅ Correct
lists:
  - path: "parent"
    keyPaths: ["id"]
  - path: "parent/children"  # Declared after parent
    keyPaths: ["id"]

# ❌ Invalid
lists:
  - path: "parent/children"  # ERROR: parent not declared yet
    keyPaths: ["id"]
  - path: "parent"
    keyPaths: ["id"]
```

### 2. Implied Parent Lists

If a keyPath implies a parent list exists, that parent must be explicitly declared:

```yaml
# ❌ Invalid - using absolute keyPaths (deprecated)
lists:
  - path: "tasks"
    keyPaths: ["projects/tasks/id"]  # ERROR: keyPaths must be relative

# ✅ Correct - using relative keyPaths
lists:
  - path: "projects"
    keyPaths: ["id"]                 # Relative to projects
  - path: "projects/tasks"
    keyPaths: ["id"]                 # Relative to projects/tasks
```

### 3. Validation Error Messages

Validation errors include specific details:

```
ValidationException: List 'departments/employees' must be declared after its parent list 'departments'

ValidationException: Invalid list rule: 'tasks' missing ancestor 'projects' (implied by keyPath 'projects/tasks/id')
```

## Advanced Examples

### Multi-Level Nesting with Complex Keys

```yaml
separator: "/"
lists:
  - path: "organizations"
    keyPaths: ["id"]
    orderBy:
      - path: "name"
        direction: asc

  - path: "organizations/departments"
    keyPaths: ["id"]
    orderBy:
      - path: "budget"
        direction: desc
        nulls: last

  - path: "organizations/departments/employees"
    keyPaths: ["id"]
    orderBy:
      - path: "level"
        direction: desc
      - path: "name"
        direction: asc
    onConflict: merge

nullPolicy:
  blanksAsNulls: true
```

This creates a three-level hierarchy: Organizations → Departments → Employees, with proper ordering and conflict resolution at each level.

## Advanced Configuration Options

### Missing KeyPath Behavior Reference

For complete details on how rows with missing keyPath values are handled, see the **"Handling Missing KeyPaths"** section under [List Rules](#handling-missing-keypaths).

**Summary:**
- Rows with missing/null keyPath values are always skipped
- A warning is logged via the Reporter SPI
- Child lists are also skipped when parent list processing is skipped
- This is the only supported behavior - there is no configuration option to change it

### Custom Separators

Configure path separators for different data formats:

```yaml
separator: "."        # Use dots instead of slashes
separator: "__"       # Use double underscores
separator: "->"       # Multi-character separator
```

**Example with dot notation:**
```yaml
separator: "."
lists:
  - path: "tasks"
    keyPaths: ["id"]
```

**Input:**
```
project.id=1, project.name=Alpha, tasks.id=t1, tasks.title=Setup
```

### Performance Tuning

```yaml
# Optimize for performance
separator: "/"          # Single-character separators are fastest
nullPolicy:
  blanksAsNulls: false  # Skip string trimming/conversion
```

## Extensibility Configuration

flat2pojo supports Service Provider Interface (SPI) extensions for custom processing:

### Value Preprocessing

Transform input data before mapping:

```yaml
# In Java code - not YAML configurable
MappingConfig config = MappingConfig.builder()
    .valuePreprocessor(Optional.of(customPreprocessor))
    .build();
```

**Use Cases:**
- Data normalization (phone numbers, emails)
- Value transformation (YES/NO → boolean)
- Field mapping (legacy_field → new_field)
- Data validation and cleaning

### Conversion Reporting

Monitor conversion process and capture warnings:

```yaml
# In Java code - not YAML configurable
MappingConfig config = MappingConfig.builder()
    .reporter(Optional.of(customReporter))
    .build();
```

**Captured Events:**
- Missing keyPath warnings
- Field conflict resolutions
- Skipped list processing
- Data quality issues

### Combined SPI Usage

```java
// Complete monitoring and preprocessing
MappingConfig config = MappingConfig.builder()
    .separator("/")
    .valuePreprocessor(Optional.of(dataCleaningPreprocessor))
    .reporter(Optional.of(auditTrailReporter))
    .lists(listRules)
    .nullPolicy(new NullPolicy(true))
    .build();
```

## Configuration Best Practices

### 1. Consistent Path Naming

```yaml
# ✅ Good: Consistent naming convention
separator: "/"
lists:
  - path: "departments"
    keyPaths: ["id"]
  - path: "departments/employees"
    keyPaths: ["id"]

# ❌ Avoid: Mixed conventions
separator: "/"
lists:
  - path: "departments"
    keyPaths: ["dept_id"]  # Inconsistent naming (should be "id")
```

### 2. Explicit Conflict Policies

```yaml
# ✅ Good: Explicit policies for clarity
lists:
  - path: "users"
    keyPaths: ["id"]
    onConflict: lastWriteWins  # Clear intention

# ❌ Unclear: Relying on defaults
lists:
  - path: "users"
    keyPaths: ["id"]
    # onConflict defaults - unclear to readers
```

### 3. Performance-Conscious Configuration

```yaml
# For high-throughput scenarios
separator: "/"              # Single-character is fastest
nullPolicy:
  blanksAsNulls: false      # Skip string processing

lists:
  - path: "items"
    keyPaths: ["id"]
    dedupe: false           # Skip deduplication if not needed
    orderBy: []             # Skip sorting if not required
```

### 4. Comprehensive Monitoring

```java
// Production-ready configuration
List<String> warnings = new ArrayList<>();
List<String> conflicts = new ArrayList<>();

Reporter detailedReporter = warning -> {
    if (warning.contains("conflict")) {
        conflicts.add(warning);
    } else {
        warnings.add(warning);
    }
    logger.info("Conversion event: {}", warning);
};

MappingConfig config = MappingConfig.builder()
    .reporter(Optional.of(detailedReporter))
    .build();

// After conversion, analyze quality
if (!warnings.isEmpty()) {
    logger.warn("Data quality issues: {}", warnings.size());
}
if (!conflicts.isEmpty()) {
    logger.info("Conflicts resolved: {}", conflicts.size());
}
```

## Related Documentation

- [OPERATIONS.md](OPERATIONS.md) - API reference, performance tuning, and production operations
- [ARCHITECTURE.md](ARCHITECTURE.md) - Design decisions and system architecture
- [PSEUDOCODE.md](PSEUDOCODE.md) - Internal algorithm flow and component interactions
- [README.md](README.md) - Project overview and quick start guide
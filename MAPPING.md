# Mapping Configuration Guide

This document provides detailed information about flat2pojo's mapping configuration system.

## List Rules

List rules define how flat data is grouped into lists and nested structures.

### Basic Structure

```yaml
lists:
  - path: "tasks"                    # Target path for the list
    keyPaths: ["tasks/id"]           # Fields that uniquely identify list elements
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
    keyPaths: ["departments/id"]

  # Child list - references parent
  - path: "departments/employees"
    keyPaths: ["departments/employees/id"]
    orderBy:
      - path: "departments/employees/name"
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
    keyPaths: ["events/date", "events/location"]  # Compound key
```

**Behavior:**
- Elements with the same composite key are merged
- Order of keyPaths matters for consistent results
- Use absolute paths from root (not relative to list path)

### OrderBy Rules

Sort list elements by one or more fields:

```yaml
lists:
  - path: "tasks"
    keyPaths: ["tasks/id"]
    orderBy:
      - path: "tasks/priority"      # Primary sort
        direction: desc             # desc or asc
        nulls: last                 # first or last
      - path: "tasks/created"       # Secondary sort
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
    keyPaths: ["users/id"]
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

- **List paths**: Relative to their parent (if nested)
- **KeyPaths**: Always absolute from root
- **OrderBy paths**: Relative to the list element

```yaml
lists:
  - path: "departments"                    # Absolute
    keyPaths: ["departments/id"]           # Absolute

  - path: "employees"                      # Relative to departments/
    keyPaths: ["departments/employees/id"] # Absolute
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
    keyPaths: ["parent/id"]
  - path: "parent/children"  # Declared after parent
    keyPaths: ["parent/children/id"]

# ❌ Invalid
lists:
  - path: "parent/children"  # ERROR: parent not declared yet
    keyPaths: ["parent/children/id"]
  - path: "parent"
    keyPaths: ["parent/id"]
```

### 2. Implied Parent Lists

If a keyPath implies a parent list exists, that parent must be explicitly declared:

```yaml
# ❌ Invalid
lists:
  - path: "tasks"
    keyPaths: ["projects/tasks/id"]  # ERROR: 'projects' list not declared

# ✅ Correct
lists:
  - path: "projects"
    keyPaths: ["projects/id"]
  - path: "projects/tasks"
    keyPaths: ["projects/tasks/id"]
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
    keyPaths: ["organizations/id"]
    orderBy:
      - path: "name"
        direction: asc

  - path: "organizations/departments"
    keyPaths: ["organizations/departments/id"]
    orderBy:
      - path: "budget"
        direction: desc
        nulls: last

  - path: "organizations/departments/employees"
    keyPaths: ["organizations/departments/employees/id"]
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
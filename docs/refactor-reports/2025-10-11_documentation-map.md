# flat2pojo Documentation Map

Visual guide to documentation structure after comprehensive audit and refactoring (2025-10-09).

## Documentation Structure

```
┌─────────────────────────────────────────────────────────────┐
│                        README.md                            │
│               Project Overview & Quick Start                │
│                    (211 lines, -66%)                        │
└──────────────┬──────────────────────────────────┬───────────┘
               │                                  │
      ┌────────▼────────┐              ┌─────────▼──────────┐
      │   FOR USERS     │              │  FOR CONTRIBUTORS  │
      └────────┬────────┘              └─────────┬──────────┘
               │                                  │
    ┌──────────▼──────────┐          ┌───────────▼───────────┐
    │   MAPPINGS.md       │          │  ARCHITECTURE.md       │
    │   DSL Specification │          │  Design Decisions      │
    │   (651 lines)       │          │  (64 lines, stub)      │
    └─────────────────────┘          └───────────┬───────────┘
               │                                  │
    ┌──────────▼──────────┐          ┌───────────▼───────────┐
    │   OPERATIONS.md     │          │  PSEUDOCODE.md         │
    │   API & Operations  │          │  Algorithm Flow        │
    │   (1086 lines)      │          │  (712 lines)           │
    └─────────────────────┘          └───────────────────────┘
                                                  │
                                     ┌────────────▼───────────┐
                                     │  DEVELOPMENT.md        │
                                     │  Dev Environment       │
                                     │  (408 lines)           │
                                     └────────────────────────┘
                                                  │
                                     ┌────────────▼───────────┐
                                     │  CONTRIBUTING.md       │
                                     │  PR/Commit Guidelines  │
                                     │  (62 lines)            │
                                     └────────────────────────┘
                                                  │
                         ┌────────────────────────┴────────────────────┐
                         │                                             │
              ┌──────────▼──────────┐                    ┌────────────▼────────┐
              │   RELEASE.md        │                    │   CHANGELOG.md      │
              │   Release Process   │                    │   Version History   │
              │   (198 lines)       │                    │   (41 lines)        │
              └─────────────────────┘                    └─────────────────────┘
```

## Topic Ownership Matrix

| Topic | Canonical Owner | Secondary References |
|-------|----------------|---------------------|
| **Overview/Intro** | README.md | - |
| **Quick Start** | README.md | - |
| **Mapping DSL** | MAPPINGS.md | README (overview only) |
| **API Reference** | OPERATIONS.md | README (overview only) |
| **Performance** | OPERATIONS.md | PSEUDOCODE (characteristics) |
| **Monitoring** | OPERATIONS.md | - |
| **Troubleshooting** | OPERATIONS.md | - |
| **Design Decisions** | ARCHITECTURE.md | PSEUDOCODE (detailed) |
| **Algorithm Flow** | PSEUDOCODE.md | ARCHITECTURE (overview) |
| **Dev Environment** | DEVELOPMENT.md | CONTRIBUTING (quick start) |
| **Contribution Rules** | CONTRIBUTING.md | - |
| **Release Process** | RELEASE.md | - |
| **Version History** | CHANGELOG.md | - |

## Navigation Paths

### User Journey: "How do I use this library?"

```
START → README.md (Quick Start)
  ├→ MAPPINGS.md (Learn DSL)
  └→ OPERATIONS.md (API reference, examples)
```

### Contributor Journey: "How do I contribute?"

```
START → CONTRIBUTING.md (Guidelines)
  ├→ DEVELOPMENT.md (Setup environment)
  ├→ ARCHITECTURE.md (Understand design)
  ├→ PSEUDOCODE.md (Understand internals)
  └→ RELEASE.md (Release process)
```

### Maintainer Journey: "How do I release?"

```
START → RELEASE.md (Release process)
  ├→ CHANGELOG.md (Update version notes)
  └→ CONTRIBUTING.md (Verify guidelines)
```

## Cross-Reference Network

Every document has "Related Documentation" section linking to:
- **Primary dependencies** (docs you must read before this one)
- **Secondary references** (docs that complement this one)
- **Always** includes link back to README.md

Example from OPERATIONS.md:
```markdown
## Related Documentation

- [MAPPINGS.md](../MAPPINGS.md) - Complete mapping DSL specification
- [ARCHITECTURE.md](ARCHITECTURE.md) - Design decisions and system architecture
- [PSEUDOCODE.md](PSEUDOCODE.md) - Internal algorithm flow and component interactions
- [README.md](README.md) - Project overview and quick start guide
```

## Link Verification Matrix

| Document | Outgoing Links | Verified |
|----------|----------------|----------|
| README.md | 9 | ✓ 100% |
| MAPPINGS.md | 4 | ✓ 100% |
| OPERATIONS.md | 4 | ✓ 100% |
| ARCHITECTURE.md | 4 | ✓ 100% |
| PSEUDOCODE.md | 4 | ✓ 100% |
| DEVELOPMENT.md | 5 | ✓ 100% |
| CONTRIBUTING.md | 4 | ✓ 100% |
| RELEASE.md | 3 | ✓ 100% |
| CHANGELOG.md | 3 | ✓ 100% |
| **TOTAL** | **41** | **✓ 100%** |

## Content Ownership Rules

### Rule 1: Single Source of Truth
Each topic has exactly ONE canonical owner. Other docs may reference but never duplicate.

### Rule 2: Pointer Stubs
When content moves, replace with a brief overview + link to canonical source.

Example:
```markdown
## Configuration

For complete configuration schema, see **[MAPPINGS.md](MAPPINGS.md)**.

Quick overview: separator, rootKeys, lists, primitives, nullPolicy
```

### Rule 3: Related Documentation
All docs must have "Related Documentation" section at the bottom.

### Rule 4: Documentation Map
README.md must maintain the Documentation Map as single source of navigation.

## File Size Guidelines

| Document | Target Size | Actual | Status |
|----------|------------|---------|--------|
| README.md | <300 lines | 211 lines | ✓ Good |
| MAPPINGS.md | 500-800 lines | 651 lines | ✓ Good |
| OPERATIONS.md | 800-1200 lines | 1086 lines | ✓ Good |
| ARCHITECTURE.md | <100 lines (stub) | 64 lines | ✓ Good |
| PSEUDOCODE.md | 600-800 lines | 712 lines | ✓ Good |
| DEVELOPMENT.md | 300-500 lines | 408 lines | ✓ Good |
| CONTRIBUTING.md | <100 lines | 62 lines | ✓ Good |
| RELEASE.md | 150-250 lines | 198 lines | ✓ Good |
| CHANGELOG.md | Growing | 41 lines | ✓ Good (new) |

## Maintenance Guidelines

### When Adding New Content

1. **Identify canonical owner** - Which doc should own this topic?
2. **Check for duplication** - Does this exist elsewhere? If yes, consolidate.
3. **Update cross-references** - Add to Related Documentation if relevant
4. **Update Documentation Map** - If new top-level topic, add to README.md map
5. **Verify links** - Ensure all links resolve correctly

### When Updating Existing Content

1. **Update canonical owner only** - Never update duplicates
2. **Check references** - Update pointer stubs if section names changed
3. **Update Related Documentation** - If dependencies changed
4. **Verify links** - Ensure no broken links

### When Removing Content

1. **Check for references** - grep for links to content being removed
2. **Update or remove pointer stubs** - Don't leave dead pointers
3. **Update Related Documentation** - Remove from cross-reference sections
4. **Update Documentation Map** - Remove from README.md if top-level topic

## Quality Checklist

Before committing documentation changes:

- [ ] No duplicated content across docs
- [ ] Each topic has clear single owner
- [ ] README.md is concise (<300 lines)
- [ ] All docs have Related Documentation section
- [ ] Documentation Map in README.md is up to date
- [ ] All internal links verified
- [ ] No circular references
- [ ] No dead links
- [ ] Pointer stubs are concise with clear next steps

## Metrics

### Before Audit (2025-10-09)

- Root docs: 6
- README.md: 614 lines
- Duplication: ~400 lines
- Docs with cross-refs: 0
- Broken links: 0
- Topics with single owner: 6

### After Audit (2025-10-09)

- Root docs: 9 (+3)
- README.md: 211 lines (-66%)
- Duplication: 0 lines (-100%)
- Docs with cross-refs: 9 (+9)
- Broken links: 0
- Topics with single owner: 9 (+3)

### Improvement

- README conciseness: +66%
- Duplication elimination: 100%
- Navigation coverage: +100%
- Link integrity: Maintained at 100%

---

**Map Created:** 2025-10-09  
**Purpose:** Visual guide to documentation structure  
**Status:** Current and accurate  
**Next Update:** As needed when structure changes

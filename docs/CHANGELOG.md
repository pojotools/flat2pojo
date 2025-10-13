# Changelog

All notable changes to flat2pojo will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Comprehensive documentation audit and refactoring
- ARCHITECTURE.md - Architecture and design decisions
- DEVELOPMENT.md - Development environment setup guide
- CHANGELOG.md - Version history (this file)
- Documentation map in README.md
- Refactoring documentation for architectural improvements

### Changed
- **Major Architecture Refactoring** - Decomposed God classes into focused, single-responsibility components
  - `PrimitiveListManager` → `PrimitiveArrayManager` with specialized helpers (PrimitiveArrayRuleCache, PrimitiveArrayNodeFactory, PrimitiveArrayBucket, PrimitiveArrayFinalizer)
  - `GroupingEngine` phased out, replaced with direct `ArrayManager` usage
  - `ArrayBucket`, `ArrayFinalizer` renamed for consistency (removed redundant "List" prefix)
  - All array management now follows consistent naming: `Primitive*` for primitives, `Array*` for objects
- **Performance Optimizations**
  - Removed unused `asArray()` method from ArrayBucket (test-only method)
  - Removed redundant `insertionOrder` field from ArrayBucket (LinkedHashMap already maintains order)
  - Optimized primitive array processing with an accumulation and sort-at-end pattern for O(P + V log V) complexity
- **Code Quality Improvements**
  - All classes now follow the Single Responsibility Principle
  - All methods maintain ≤4 parameter guideline (using context objects where needed)
  - Small, focused functions (~4-6 lines guideline)
  - ≤1 indent level in most methods
  - Consistent dependency injection patterns
- README.md - Trimmed to overview/quickstart only, moved detailed content to canonical homes
- MAPPING.md renamed to MAPPINGS.md for consistency
- All docs updated with cross-references and "Related Documentation" sections
- Improved navigation with clear single-source ownership

### Fixed
- Documentation duplication across README, MAPPING, and OPERATIONS
- Inconsistent doc cross-references
- God class anti-patterns in array management
- Redundant data structures and unused code
- Parameter count violations (>4 parameters)

### Technical Debt Reduction
- Eliminated architectural inconsistencies between primitive and non-primitive array processing
- Removed facade layer (GroupingEngine) in favor of direct manager usage
- Cleaned up naming conventions for clarity and consistency

## [0.3.0] - 2024-XX-XX

See git history for previous releases.

## Release Notes

For detailed release notes and migration guides, see individual version entries above.

For the release process, see [RELEASE.md](RELEASE.md).

## Related Documentation

- [RELEASE.md](RELEASE.md) - Release process and versioning
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines
- [README.md](README.md) - Project overview

# Contributing to flat2pojo

Thanks for your interest in contributing! Here's how to get started.

## Development Setup

See **[DEVELOPMENT.md](DEVELOPMENT.md)** for complete development environment setup, build instructions, and code quality tools.

Quick start:

```bash
# Clone and build
git clone https://github.com/pojotools/flat2pojo.git
cd flat2pojo
mvn clean verify
```

## Project Structure

- `flat2pojo-core/` - Core conversion logic
- `flat2pojo-jackson/` - Jackson integration (main library)
- `flat2pojo-spi/` - Extension interfaces
- `flat2pojo-examples/` - Usage examples and tests
- `flat2pojo-benchmarks/` - Performance tests

## Making Changes

1. **Fork** the repository
2. **Create** a feature branch: `git checkout -b feature/your-feature`
3. **Write** tests for your changes
4. **Ensure** all tests pass: `mvn clean verify`
5. **Format** code: `mvn spotless:apply`
6. **Submit** a pull request

## Code Standards

- All new code must have tests
- Maintain 80%+ test coverage
- Follow existing code style (Google Java Format)
- Update documentation for public APIs
- No breaking changes without discussion

## Architecture Understanding

Before making significant changes, review:

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Design decisions and system architecture
- **[PSEUDOCODE.md](PSEUDOCODE.md)** - Internal algorithm flow and component interactions

## Release Process

For maintainers, see **[RELEASE.md](RELEASE.md)** for release process and versioning guidelines.

## Questions?

Open an issue for discussion before starting large changes.

## Related Documentation

- [DEVELOPMENT.md](DEVELOPMENT.md) - Development environment and build setup
- [ARCHITECTURE.md](ARCHITECTURE.md) - System design and architecture
- [RELEASE.md](RELEASE.md) - Release process for maintainers
- [README.md](README.md) - Project overview
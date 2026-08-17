# Changelog

## [1.2.3] - 2026-08-17

### Fixed

- `containsAll` is consistent with the hierarchical `contains` for Java
  callers: it is true iff `contains` holds for every element of the collection.

## [1.2.2] - 2026-07-12

### Changed

- Migrate the build to deps.edn and tools.build, with Leiningen supported via lein-tools-deps.

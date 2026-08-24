# Changelog

## [1.3.0] - 2026-08-24

### Added

- Direct `parent`, `children`, `roots`, and `leaves` hierarchy-navigation
  functions backed by the internal parent index.
- A benchmark-proven confirmation that `conj` and `disj` perform well at
  10k-member scale, retained as an opt-in benchmark test.
- A generative/property-based test suite using test.check to verify hierarchy
  invariants across random `conj` and `disj` sequences.
- A `validate!`/`valid-hierarchy?` diagnostic API for detecting invalid
  sort-order and containment relationships.
- `union`, `intersection`, and `difference` set-algebra operations for
  compatible HierSets.
- `clojure.datafy/Datafy` support and versioned EDN serialization round-trip.
- Expanded API docstrings and new README “Common workflows” examples.

## [1.2.3] - 2026-08-17

### Fixed

- `containsAll` is consistent with the hierarchical `contains` for Java
  callers: it is true iff `contains` holds for every element of the collection.

## [1.2.2] - 2026-07-12

### Changed

- Migrate the build to deps.edn and tools.build, with Leiningen supported via lein-tools-deps.

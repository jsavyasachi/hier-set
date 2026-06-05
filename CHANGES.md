# Changes

## 1.2.0

Maintenance/modernization release (fork).

- **Compatibility with modern JDKs.** Hinted the `toArray(T[])` method so the
  type compiles on JDK 11 and later; it had been broken since Java 11 added
  `Collection.toArray(IntFunction)`, which made the single-argument `toArray`
  overload ambiguous.
- **Correct `java.util.Set` hash code.** `hashCode` now returns the sum of the
  members' hash codes, as the `Set` contract requires, so a `HierSet` and an
  equal `java.util.HashSet` hash identically. It previously used Clojure's
  collection hash, breaking the contract.
- Target Clojure 1.12.5; replaced the EOL 1.4/1.5/1.6 test profiles with
  1.10.3 / 1.11.4 / 1.12.5.
- Added GitHub Actions CI: JDK 8/11/17/21 × Clojure 1.10.3/1.11.4/1.12.5.
- Removed the unused `ILookup` import.
- Expanded the test suite from 30 to 61 assertions (comparator, metadata,
  `java.util.Set` and `Sorted` interop, not-found semantics, immutability).
- Added `:min-lein-version` and `:pedantic? :warn`.

## 1.1.2

- Fixed a bad `pom.xml`.

## 1.1.1

- Fixed an infinite-recursion bug in `HierSet`'s `equals` method.

## 1.1.0

- Implemented the persistent/`java.util.Set` interfaces; added `conj`/`disj`
  support and tests.

## 1.0.0

- Initial release.

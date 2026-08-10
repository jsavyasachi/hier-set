# hier-set

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.savya/hier-set.svg)](https://clojars.org/net.clojars.savya/hier-set)
[![cljdoc](https://cljdoc.org/badge/net.clojars.savya/hier-set)](https://cljdoc.org/d/net.clojars.savya/hier-set/CURRENT)
[![test](https://github.com/jsavyasachi/hier-set/actions/workflows/test.yml/badge.svg)](https://github.com/jsavyasachi/hier-set/actions/workflows/test.yml)

A "hierarchical set" data structure for Clojure has elements in a defined
hierarchical relationship. An element is a member if it is a primary member or
a descendant of a primary member. Lookup returns set membership *and* all
primary members that are ancestors of the lookup element.

## Stack

<a href="https://clojure.org"><img src="https://img.shields.io/badge/Clojure-5881D8?style=flat&logo=clojure&logoColor=fff" alt="Clojure" /></a>
<a href="https://clojure.org/guides/deps_and_cli"><img src="https://img.shields.io/badge/deps.edn-5881D8?style=flat&logo=clojure&logoColor=fff" alt="deps.edn" /></a>
<a href="https://clojure.github.io/tools.build/"><img src="https://img.shields.io/badge/tools.build-5881D8?style=flat&logo=clojure&logoColor=fff" alt="tools.build" /></a>

## Why

The element sort-order and a separate containment predicate define the
hierarchical relationship. These constraints apply:

* Elements must sort before any descendants.
* Elements must contain all elements that sort between themselves and any
  descendant.

Use this library for simple hierarchical systems. The hierarchy is implicit in
the entities, such as the Java package system, hierarchical filesystems, or IP
networks. Do not use this library for complex ad hoc hierarchies, such as
relationships between classes with multiple inheritance.

## Installation

Clojure CLI (`deps.edn`):

```clj
net.clojars.savya/hier-set {:mvn/version "1.2.2"}
```

Leiningen (`project.clj`):

```clj
[net.clojars.savya/hier-set "1.2.2"]
```

Run tests with `clojure -M:test`. Build the JAR with
`clojure -T:build jar`, or deploy with `clojure -T:build deploy`.

## Usage

Use the `hier-set` and `hier-set-by` constructor functions in the
`hier-set.core` namespace. The `hier-set.core/ancestors` and
`hier-set.core/descendants` functions return lazy sequences of the ancestors
and descendants of a provided key.

## Compatibility

The library requires Clojure 1.10 or later and JDK 8 or later. The library is
continuously tested against Clojure 1.10.3, 1.11.4, and 1.12.5 on JDK 8, 11,
17, and 21.

## Example

A basic example:

```clj
(ns example.hier-set
  (:require [hier-set.core :as hs :refer [hier-set]]))

(def starts-with? #(.startsWith %2 %1))

(def h (hier-set starts-with? "ack" "foo" "foo.bar" "quux"))

(get h "bar")              ;;=> nil
(get h "foo")              ;;=> ("foo")
(get h "foo.bar.baz")      ;;=> ("foo.bar" "foo")
(hs/ancestors h "bar")     ;;=> ()
(hs/ancestors h "foo.baz") ;;=> ("foo")
(hs/descendants h "foo")   ;;=> ("foo" "foo.bar")
```

## License

Copyright © 2012, 2014 Marshall Bockrath-Vandegrift.

Maintenance fork (2026) by Savyasachi, original: https://github.com/llasram/hier-set.
Distributed under the [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html), preserving the original license.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

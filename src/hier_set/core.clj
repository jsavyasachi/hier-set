(ns hier-set.core
  "Provides a 'hierarchical set' data structure. See `hier-set` for details."
  (:refer-clojure :exclude [descendants ancestors])
  (:import [java.util Set])
  (:import [clojure.lang IFn IObj IPersistentCollection IPersistentSet
                         PersistentTreeSet Seqable Sorted]))

(defprotocol Hierarchical
  "Defines operations on collections with hierarchical relationships."
  (ancestors [coll key] [coll key strict?]
    "Returns a lazy sequence of ancestors of `key` in a Hierarchical `coll`.
`key` must be a value comparable to the collection's elements. With the
two-argument arity, `key` itself is included when present; pass the boolean
`strict?` as true to omit it. The result is ordered from the nearest ancestor
to the farthest, and is empty when no primary member contains `key`.")
  (descendants [coll key] [coll key strict?]
    "Returns a lazy sequence of primary members below `key` in a Hierarchical
`coll`. `key` must be a value comparable to the collection's elements. With
the two-argument arity, `key` itself is included when present; pass the
boolean `strict?` as true to omit it. The result is in the collection's sort
order and is empty when no primary member is a descendant of `key`."))

(defn- strictify
  [f coll key strict?]
  (let [result (f coll key)]
    (if (and strict? (= key (first result)))
      (drop 1 result)
      result)))

(deftype HierSet [meta hcontains? ^PersistentTreeSet contents parents]
  ;; meta - the IObj metadata of the instance
  ;; hcontains? - the containment predicate function
  ;; contents - the sorted set of primary HierSet members
  ;; parents - map of members to their immediate parent members

  Object
  (toString [this] (str contents))
  (hashCode [this] (.hashCode contents))
  (equals [this other]
    (.equals contents other))

  IObj
  (meta [this] meta)
  (withMeta [this meta]
    (HierSet. meta hcontains? contents parents))

  Hierarchical
  (ancestors [this key]
    (letfn [(ancestors-of [k]
              (when k (cons k (lazy-seq (ancestors-of (parents k))))))
            (not-ancestor? [k] (not (hcontains? k key)))]
      (let [sibling (first (rsubseq contents <= key))]
        (->> sibling ancestors-of (drop-while not-ancestor?)))))
  (ancestors [this key strict?]
    (strictify ancestors this key strict?))
  (descendants [this key]
    (take-while #(hcontains? key %) (subseq contents >= key)))
  (descendants [this key strict?]
    (strictify descendants this key strict?))

  Seqable
  (seq [this] (seq contents))

  IPersistentCollection
  (count [this] (count contents))
  (cons [this key]
    (if (contains? contents key)
      this
      (let [parent (first (ancestors this key))
            kids (filter #(= parent (parents %)) (descendants this key))
            parents (reduce #(assoc %1 %2 key) (assoc parents key parent) kids)
            contents (conj contents key)]
        (HierSet. meta hcontains? contents parents))))
  (empty [this]
    (HierSet. meta hcontains? (empty contents) (empty parents)))
  (equiv [this other]
    (.equals this other))

  IPersistentSet
  (disjoin [this key]
    (if-not (contains? contents key)
      this
      (let [parent (parents key), contents (disj contents key)
            kids (filter #(= key (parents %)) (descendants this key)),
            parents (reduce #(assoc %1 %2 parent) (dissoc parents key) kids)]
        (HierSet. meta hcontains? contents parents))))
  (contains [this key]
    (boolean (.get this key)))
  (get [this key]
    (seq (ancestors this key)))

  Set
  (containsAll [this coll] (every? #(.contains this %) coll))
  (isEmpty [this] (.isEmpty contents))
  (iterator [this] (.iterator contents))
  (size [this] (.size contents))
  (toArray [this] (.toArray contents))
  (^objects toArray [this ^objects a] (.toArray contents a))

  Sorted
  (comparator [this] (.comparator contents))
  (entryKey [this entry] entry)
  (seq [this ascending] (.seq contents ascending))
  (seqFrom [this key ascending] (.seqFrom contents key ascending))

  IFn
  (invoke [this key]
    (get this key))
  (invoke [this key not-found]
    (get this key not-found)))

(defn hier-set-by
  "Creates a HierSet using `comparator` for element comparison.

`hcontains?` must be a two-argument function accepting two values of the
element type and returning true when its first argument contains its second.
`comparator` must accept two element values and return the usual negative,
zero, or positive comparison result. The comparator is not merely a display
order: it defines the order used to find ancestors and descendants. It must
place every element before its descendants, and each ancestor must contain all
elements sorted between it and those descendants. Comparator equality (a zero
result) also makes two values the same set member, as with sorted-set-by.

`keys` are the initial primary members. `conj` and `disj` are persistent
operations: each returns a new HierSet and leaves its input unchanged. Unlike
a mutable Java Set, the Java mutator methods are unsupported. HierSet's
`contains` also recognizes descendants, while `get` and function invocation
return the lazy sequence of primary ancestors rather than the queried value."
  [hcontains? comparator & keys]
  (letfn [(find-parent [[parents ancestors] key]
            (let [not-ancestor? (fn [k] (not (hcontains? k key)))
                  ancestors (drop-while not-ancestor? ancestors)]
              [(assoc parents key (first ancestors)) (cons key ancestors)]))]
    (let [contents (apply sorted-set-by comparator keys)
          parents (first (reduce find-parent [{} ()] contents))]
      (HierSet. nil hcontains? contents parents))))

(defn hier-set
  "Creates a hierarchical set with the containment predicate `hcontains?` and
primary members `keys`. The `hcontains?` predicate should be a function with
two arguments of the set element type. It should return `true` if the first
argument contains the second, and false otherwise.

A hierarchical set is a set of elements that can contain other elements
hierarchically. The element sort-order and the `hcontains?` predicate define
the hierarchical relationship. These constraints apply: (a) elements must sort
before any descendants; and (b) elements must contain all elements that sort
between themselves and any descendant. This means `(hcontains? x x)` must be
true. Elements are both ancestors and descendants of themselves.

Lookup in the set returns a seq of all primary members that are ancestors of the
provided key. It returns nil if the provided key is not a descendant of a
primary member.

`conj` and `disj` return new HierSets and never mutate the original. This is
persistent behavior, even though HierSet also implements `java.util.Set`;
that Java interface's mutator methods are unsupported. Unlike a standard
sorted set, `contains` is true for a descendant that is not a primary member,
and `get` returns the primary ancestors of its argument."
  [hcontains? & keys] (apply hier-set-by hcontains? compare keys))

(ns hier-set.core
  "Provides a 'hierarchical set' data structure. See `hier-set` for details."
  (:refer-clojure :exclude [descendants ancestors])
  (:require [clojure.core.protocols]
            [clojure.datafy]
            [clojure.edn :as edn])
  (:import [java.util Set])
  (:import [clojure.lang IFn IObj IPersistentCollection IPersistentSet
                         PersistentTreeSet Seqable Sorted]))

(defprotocol Hierarchical
  "Defines operations on collections with hierarchical relationships."
  (ancestors [coll key] [coll key strict?]
    "Returns a lazy sequence of all ancestors of `key` in `coll`. Do not
include `key` when `strict?` is true. The default is false.")
  (descendants [coll key] [coll key strict?]
    "Returns a lazy sequence of all descendants of `key` in `coll`. Do not
include `key` when `strict?` is true. The default is false."))

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

  clojure.core.protocols/Datafiable
  (datafy [this]
    {:members (vec contents)
     :metadata meta})

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

(defn parent
  "Returns the immediate parent of `key`, or nil when it has no parent."
  [^HierSet coll key]
  (get (.parents coll) key))

(defn children
  "Returns the immediate children of `key` in sorted order."
  [^HierSet coll key]
  (let [parents (.parents coll)]
    (filter #(= key (get parents %)) (.contents coll))))

(defn roots
  "Returns the members without parents in sorted order."
  [^HierSet coll]
  (let [parents (.parents coll)]
    (filter #(nil? (get parents %)) (.contents coll))))

(defn leaves
  "Returns the members without children in sorted order."
  [^HierSet coll]
  (let [parents (set (vals (.parents coll)))]
    (remove parents (.contents coll))))

(defn datafy
  "Returns an inspectable map representation of a `HierSet`."
  [^HierSet coll]
  (clojure.datafy/datafy coll))

(defn- serializable-member?
  [member]
  (or (string? member) (keyword? member)))

(declare hier-set)

(defn ->edn
  "Serializes a `HierSet`'s primary members to a versioned EDN string.

  The containment predicate is intentionally not serialized; callers must
  provide it to `edn->hier-set`. Only sets using natural ordering and string
  or keyword members are supported. Sets with custom comparators are rejected
  because a comparator function cannot be safely or faithfully encoded in EDN.
  Metadata is not part of this primary-member serialization format."
  [coll]
  (when-not (instance? HierSet coll)
    (throw (ex-info "Expected a HierSet" {:value coll})))
  (let [^HierSet coll coll
        ^PersistentTreeSet contents (.-contents coll)
        members (vec contents)]
    (when-not (identical? compare (.comparator contents))
      (throw (ex-info "Cannot serialize a HierSet with a custom comparator"
                      {:comparator (.comparator contents)})))
    (when-not (every? serializable-member? members)
      (throw (ex-info "HierSet members must be strings or keywords for EDN serialization"
                      {:members members})))
    (pr-str {:hier-set/version 1
             :members members})))

(defn edn->hier-set
  "Deserializes `edn` using `hcontains?` and natural member ordering.

  `edn` must be a version 1 payload produced by `->edn`. The containment
  predicate is not serialized and must be supplied by the caller."
  [hcontains? edn]
  (when-not (string? edn)
    (throw (ex-info "HierSet EDN must be a string" {:edn edn})))
  (let [payload (try
                  (edn/read-string edn)
                  (catch Exception e
                    (throw (ex-info "Invalid HierSet EDN" {:edn edn} e))))]
    (when-not (and (map? payload)
                   (= #{:hier-set/version :members} (set (keys payload)))
                   (= 1 (:hier-set/version payload))
                   (contains? payload :members)
                   (vector? (:members payload))
                   (every? serializable-member? (:members payload)))
      (throw (ex-info "Invalid HierSet EDN payload" {:payload payload})))
    (try
      (apply hier-set hcontains? (:members payload))
      (catch Exception e
        (throw (ex-info "HierSet EDN members violate the natural-order contract"
                        {:members (:members payload)} e))))))

(defn hier-set-by
  "Like hier-set, but specifies the comparator for element comparison."
  [hcontains? comparator & keys]
  (letfn [(find-parent [[parents ancestors] key]
            (let [not-ancestor? (fn [k] (not (hcontains? k key)))
                  ancestors (drop-while not-ancestor? ancestors)]
              [(assoc parents key (first ancestors)) (cons key ancestors)]))]
    (let [contents (apply sorted-set-by comparator keys)
          parents (first (reduce find-parent [{} ()] contents))]
      (HierSet. nil hcontains? contents parents))))

(defn validate!
  "Validates the ordering, containment, and parent-index invariants of a
  `HierSet`. Returns true when valid and throws `ExceptionInfo` describing the
  first violated invariant otherwise."
  [^HierSet coll]
  (let [^PersistentTreeSet contents (.-contents coll)
        hcontains? (.-hcontains_QMARK_ coll)
        parents (.-parents coll)
        comparator (or (.comparator contents) compare)
        compare-members (if (ifn? comparator)
                         comparator
                         (fn [a b]
                           (.compare ^java.util.Comparator comparator a b)))
        fail (fn [invariant message data]
               (throw (ex-info message (assoc data :invariant invariant))))]
    (doseq [ancestor contents
            descendant contents
            :when (and (not= ancestor descendant)
                       (hcontains? ancestor descendant))]
      (when (pos? (compare-members ancestor descendant))
        (fail :sort-order
              (str "sort order places ancestor " ancestor
                   " after descendant " descendant)
              {:ancestor ancestor :descendant descendant}))
      (doseq [between (subseq contents >= ancestor <= descendant)]
        (when-not (hcontains? ancestor between)
          (fail :containment
                (str "ancestor " ancestor " does not contain member " between
                     " between it and descendant " descendant)
                {:ancestor ancestor :member between :descendant descendant}))))
    (let [expected (first (reduce (fn [[expected-parents ancestors] key]
                                   (let [parent (first (drop-while
                                                        #(not (hcontains? % key))
                                                        ancestors))]
                                     [(assoc expected-parents key parent)
                                      (cons key ancestors)]))
                                 [{} ()]
                                 contents))]
      (doseq [[member expected-parent] expected]
        (when-not (= expected-parent (get parents member ::missing))
          (fail :parent-index
                (str "parent index maps member " member " to "
                     (pr-str (get parents member)) "; expected "
                     (pr-str expected-parent))
                {:member member
                 :actual-parent (get parents member)
                 :expected-parent expected-parent})))
      (doseq [member (keys parents)
              :when (not (contains? expected member))]
        (fail :parent-index
              (str "parent index contains unexpected member " member)
              {:member member})))
    true))

(defn valid-hierarchy?
  "Returns true when `coll` satisfies all `HierSet` invariants."
  [^HierSet coll]
  (try
    (validate! coll)
    (catch clojure.lang.ExceptionInfo _
      false)))

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
primary member."
  [hcontains? & keys] (apply hier-set-by hcontains? compare keys))

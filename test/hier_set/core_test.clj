(ns hier-set.core-test
  (:require [clojure.datafy :as datafy]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [hier-set.core :as hs])
  (:use [hier-set.core :only [hier-set hier-set-by]])
  (:use [clojure.test]))

(defn with-starts?
  "Does the string s start with the provided prefix?"
  {:inline (fn [prefix s & to]
             `(let [^String s# ~s, ^String prefix# ~prefix]
                (.startsWith s# prefix# ~@(when (seq to) [`(int ~@to)]))))
   :inline-arities #{2 3}}
  ([prefix s] (.startsWith ^String s ^String prefix))
  ([prefix s to] (.startsWith ^String s ^String prefix (int to))))

(deftest test-fundamental
  (let [hs (hier-set with-starts?)]
    (testing "Compares equal to other sets"
      (is (= #{} hs))
      (is (= hs #{})))
    (is (= "#{}" (str hs)) "Represents as a string")))

(deftest test-basic
  (let [hs (hier-set with-starts? "foo" "foo.bar" "foo.bar.baz" "quux")]
    (testing "Gets ancestor primary elements"
      (testing "using `get`"
        (is (= nil (get hs "bar")))
        (is (= '("foo") (get hs "foo.baz")))
        (is (= '("foo.bar" "foo") (get hs "foo.bar.bar"))))
      (testing "by calling the set as a function"
        (is (= nil (hs "bar")))
        (is (= '("foo") (hs "foo.baz")))
        (is (= '("foo.bar" "foo") (hs "foo.bar.bar"))))
      (testing "using `ancestors`"
        (is (= '() (hs/ancestors hs "bar")))
        (is (= '("foo") (hs/ancestors hs "foo.baz")))
        (is (= '("foo.bar" "foo") (hs/ancestors hs "foo.bar.bar")))))
    (testing "Gets descendant primary elements"
      (testing "using `descendants`"
        (is (= '() (hs/descendants hs "bar")))
        (is (= '("foo.bar" "foo.bar.baz") (hs/descendants hs "foo.bar")))))))

(deftest test-parent-child-queries
  (let [hs (hier-set with-starts?
                     "foo" "foo.bar" "foo.bar.baz" "foo.quux"
                     "quux")]
    (testing "parent returns the immediate parent"
      (is (= "foo.bar" (hs/parent hs "foo.bar.baz")))
      (is (nil? (hs/parent hs "foo")))
      (is (nil? (hs/parent hs "missing"))))
    (testing "children returns immediate children in member order"
      (is (= '("foo.bar" "foo.quux") (hs/children hs "foo")))
      (is (= '("foo.bar.baz") (hs/children hs "foo.bar")))
      (is (= '() (hs/children hs "missing"))))
    (testing "roots returns members without parents in member order"
      (is (= '("foo" "quux") (hs/roots hs))))
    (testing "leaves returns members without children in member order"
      (is (= '("foo.bar.baz" "foo.quux" "quux") (hs/leaves hs)))))
  (testing "empty sets have no roots or leaves"
    (let [hs (hier-set with-starts?)]
      (is (= '() (hs/roots hs)))
      (is (= '() (hs/leaves hs))))))

(def ^:private testing-data
  ["adam" "adam.nested" "adam.nested.deeply"
   "betty"
   "david" "david.nested.deeply"
   "erin.nested"])

(defn- benchmark-data
  "Builds a wide hierarchy with a deep path in each branch."
  []
  (for [branch (range 50)
        member (range 200)]
    (let [root (format "branch-%02d" branch)]
      (cond
        (zero? member) root
        (< member 20) (str root (apply str (map #(format ".%02d" %) (range 1 (inc member)))))
        :else (str root (apply str (map #(format ".%02d" %) (range 1 20)))
                   (format ".leaf-%03d" (- member 20)))))))

(defn- benchmark-time
  [f value]
  (dotimes [_ 3]
    (f value))
  (let [start (System/nanoTime)]
    (dotimes [_ 5]
      (f value))
    (/ (- (System/nanoTime) start) 5e6)))

(deftest ^:benchmark test-large-update-performance
  (let [members (benchmark-data)
        baseline (apply hier-set with-starts? members)
        broad-key "branch"
        branch-key "branch-00"
        leaf-key "branch-00.01.02.03.04.05.06.07.08.09.10.11.12.13.14.15.16.17.18.19.leaf-000"
        conj-time (benchmark-time #(conj % broad-key) baseline)
        disj-time (benchmark-time #(disj % branch-key) baseline)
        conj-result (conj baseline broad-key)
        disj-result (disj baseline branch-key)]
    (println (format "large-update benchmark: members=%d conj-ms=%.3f disj-ms=%.3f"
                     (count baseline) conj-time disj-time))
    (is (= 10001 (count conj-result)))
    (is (= 9999 (count disj-result)))
    (is (= 22 (count (get conj-result leaf-key))))
    (is (= "branch" (last (get conj-result leaf-key))))
    (is (= '("branch-00.01") (get disj-result "branch-00.01")))))

(deftest test-modification
  (let [orig (apply hier-set with-starts? testing-data)]
    (testing "Adds elements to the set"
      (testing "with no existing relationship"
        (let [updated (conj orig "chris")]
          (is (= nil (get orig "chris")))
          (is (= '("chris") (get updated "chris")))))
      (testing "with existing ancestors"
        (let [updated (conj orig "betty.nested")
              test-key "betty.nested.deeply"]
          (is (= '("betty") (get orig test-key)))
          (is (= '("betty.nested" "betty") (get updated test-key)))))
      (testing "with existing descendants"
        (let [updated (conj orig "erin")
              test-key "erin.nested.deeply"]
          (is (= '("erin.nested") (get orig test-key)))
          (is (= '("erin.nested" "erin") (get updated test-key)))))
      (testing "with existing ancestors and descendants"
        (let [updated (conj orig "david.nested")
              test-key "david.nested.deeply"]
          (is (= '("david.nested.deeply" "david") (get orig test-key)))
          (is (= '("david.nested.deeply" "david.nested" "david")
                 (get updated test-key))))))
    (testing "Removes elements from the set"
      (testing "with no existing relationship"
        (let [updated (disj orig "betty")]
          (is (= '("betty") (get orig "betty")))
          (is (= nil (get updated "betty")))))
      (testing "with existing ancestors"
        (let [updated (disj orig "david.nested.deeply")
              test-key "david.nested.deeply"]
          (is (= '("david.nested.deeply" "david") (get orig test-key)))
          (is (= '("david") (get updated test-key)))))
      (testing "with existing descendants"
        (let [updated (disj orig "david")
              test-key "david.nested.deeply"]
          (is (= '("david.nested.deeply" "david") (get orig test-key)))
          (is (= '("david.nested.deeply") (get updated test-key)))))
      (testing "with existing ancestors and descendants "
        (let [updated (disj orig "adam.nested")
              test-key "adam.nested.deeply"]
          (is (= '("adam.nested.deeply" "adam.nested" "adam")
                 (get orig test-key)))
          (is (= '("adam.nested.deeply" "adam") (get updated test-key))))))))

(deftest test-not-found-semantics
  ;; Makes sure `get`/invoke use a not-found default. This test lets us remove
  ;; the unused `ILookup` import without a regression. An incorrect `valAt`
  ;; implementation changes the test result.
  (let [hs (hier-set with-starts? "foo" "foo.bar")]
    (testing "not-found is returned for non-descendants"
      (is (= :missing (get hs "nope" :missing)))
      (is (= :missing (hs "nope" :missing))))
    (testing "members ignore not-found and return their ancestors"
      (is (= '("foo") (get hs "foo.baz" :missing)))
      (is (= '("foo") (hs "foo.baz" :missing))))))

(deftest test-hier-set-by
  ;; `hier-set` delegates to `hier-set-by` with `compare`. Test `hier-set-by`
  ;; directly and confirm that it uses the sorted order.
  (let [hs (hier-set-by with-starts? compare "foo" "foo.bar" "quux")]
    (testing "behaves like hier-set"
      (is (= '("foo") (get hs "foo.baz")))
      (is (= '("foo.bar" "foo") (get hs "foo.bar.x"))))
    (testing "members use comparator order"
      (is (= '("foo" "foo.bar" "quux") (seq hs))))))

(deftest test-metadata
  (let [hs  (hier-set with-starts? "foo" "foo.bar")
        hs2 (with-meta hs {:a 1})]
    (testing "metadata round-trips through IObj"
      (is (nil? (meta hs)))
      (is (= {:a 1} (meta hs2))))
    (testing "with-meta keeps contents"
      (is (= (seq hs) (seq hs2)))
      (is (= '("foo") (get hs2 "foo.baz"))))))

(deftest test-collection-ops
  (let [hs (hier-set with-starts? "foo" "foo.bar" "quux")]
    (testing "count"
      (is (= 3 (count hs))))
    (testing "empty returns an empty hier-set"
      (is (= 0 (count (empty hs))))
      (is (empty? (empty hs))))
    (testing "seq yields ascending members"
      (is (= '("foo" "foo.bar" "quux") (seq hs))))))

(deftest test-java-set-interop
  (let [^java.util.Set hs (hier-set with-starts? "foo" "foo.bar" "quux")]
    (testing "size / isEmpty"
      (is (= 3 (.size hs)))
      (is (false? (.isEmpty hs)))
      (is (true? (.isEmpty ^java.util.Set (hier-set with-starts?)))))
    (testing "iterator returns ascending members"
      (is (= '("foo" "foo.bar" "quux") (iterator-seq (.iterator hs)))))
    (testing "toArray (no-arg) and toArray(T[]) return all members"
      ;; The two-argument form tests the JDK 11+ toArray fix.
      (is (= #{"foo" "foo.bar" "quux"} (set (.toArray hs))))
      (is (= #{"foo" "foo.bar" "quux"} (set (.toArray hs ^objects (make-array Object 0))))))
    (testing "containsAll"
      (is (true? (.containsAll hs ["foo" "quux"])))
      (is (false? (.containsAll hs ["foo" "nope"])))
      (testing "uses hierarchical contains semantics"
        (let [all-contained ["foo.baz" "foo.bar.baz"]
              one-not-contained ["foo.baz" "nope"]]
          (is (every? #(.contains hs %) all-contained))
          (is (true? (.containsAll hs all-contained)))
          (is (not-every? #(.contains hs %) one-not-contained))
          (is (false? (.containsAll hs one-not-contained))))))))

(deftest test-sorted-protocol
  (let [^clojure.lang.Sorted hs (hier-set with-starts? "a" "b" "c" "d")]
    (testing "comparator is available"
      (is (some? (.comparator hs))))
    (testing "seq uses the direction"
      (is (= '("a" "b" "c" "d") (seq (.seq hs true))))
      (is (= '("d" "c" "b" "a") (seq (.seq hs false)))))
    (testing "seqFrom starts with the key"
      (is (= '("b" "c" "d") (seq (.seqFrom hs "b" true))))
      (is (= '("b" "a") (seq (.seqFrom hs "b" false)))))))

(deftest test-set-hashcode-contract
  ;; java.util.Set requires hashCode to equal the sum of member hash codes. An
  ;; equal HashSet must have the same hash code.
  (let [hs  (hier-set with-starts? "foo" "bar" "baz")
        ref (java.util.HashSet. ["foo" "bar" "baz"])]
    (is (= hs ref))
    (is (= (.hashCode ^java.util.Set hs) (.hashCode ref)))))

(deftest test-immutability
  ;; HierSet does not implement the mutators of java.util.Set. A call to a
  ;; mutator throws. This test shows that a HierSet is immutable through the Set interface.
  (let [^java.util.Set hs (hier-set with-starts? "foo")]
    (is (thrown? AbstractMethodError (.add hs "x")))
    (is (thrown? AbstractMethodError (.remove hs "foo")))
    (is (thrown? AbstractMethodError (.clear hs)))))

(def ^:private path-segment-gen
  (gen/elements ["a" "b" "c" "d" "e" "f"]))

(def ^:private path-gen
  (gen/fmap #(clojure.string/join "." %)
            (gen/vector path-segment-gen 1 3)))

(defn- expected-ancestors
  [members key]
  (->> members
       sort
       (filter #(with-starts? % key))
       reverse))

(defn- invariant-violations
  "Returns descriptions instead of using `is`, so this can test bad models too."
  [coll expected-members query-keys]
  (let [members (vec expected-members)
        actual-members (vec (seq coll))
        actual-parents (.-parents ^hier_set.core.HierSet coll)
        expected-parent-keys (set members)]
    (cond-> []
      (not= actual-members (vec (sort members)))
      (conj :iteration-order)

      (not= (set (keys actual-parents)) expected-parent-keys)
      (conj :orphaned-parent-index-entry)

      (some #(not= (seq (hs/ancestors coll %))
                   (expected-ancestors members %))
            members)
      (conj :member-ancestor-chain)

      (some (fn [key]
              (not= (seq (get coll key))
                   (seq (expected-ancestors members key))))
            query-keys)
      (conj :lookup-containment)

      (some (fn [key]
              (not= (seq (hs/descendants coll key))
                   (filter #(with-starts? key %) (sort members))))
            members)
      (conj :member-descendant-range))))

(deftest test-invariant-checker-detects-invalid-reference-model
  (let [coll (hier-set with-starts? "a" "a.b" "c")]
    (is (contains? (set (invariant-violations coll ["a.b" "c"] []))
                   :iteration-order))))

(defn- apply-operation
  [coll members [operation key]]
  (case operation
    :conj [(conj coll key) (conj members key)]
    :disj [(disj coll key) (disj members key)]))

(def ^:private operation-gen
  (gen/let [pool (gen/vector-distinct path-gen {:min-elements 1 :max-elements 24})
            initial (gen/fmap set (gen/vector (gen/elements pool) 0 24))
            operations (gen/vector (gen/tuple (gen/elements [:conj :disj])
                                             (gen/elements pool))
                                   1 80)]
    [pool initial operations]))

(defn- check-after-every-operation
  [[pool initial operations]]
  (loop [coll (apply hier-set with-starts? initial)
         members initial
         remaining operations]
    (if (seq (invariant-violations coll members pool))
      false
      (if-let [operation (first remaining)]
        (let [[next-coll next-members] (apply-operation coll members operation)]
          (recur next-coll next-members (next remaining)))
        true))))

(deftest property-random-hierarchies-and-mutations
  (let [result (tc/quick-check 100
                               (prop/for-all [case operation-gen]
                                 (check-after-every-operation case)))]
    (is (:result result) (pr-str result))))

(deftest test-datafy
  (let [hs (with-meta (hier-set with-starts? "foo" "foo.bar" "quux")
             {:source :test})]
    (is (= {:members ["foo" "foo.bar" "quux"]
            :metadata {:source :test}}
           (datafy/datafy hs))))
  (is (= {:members [] :metadata nil}
         (datafy/datafy (hier-set with-starts?)))))

(deftest test-edn-round-trip
  (let [original (with-meta (hier-set with-starts?
                                      "foo" "foo.bar" "foo.bar.baz" "quux")
                            {:ignored :by-serialization})
        restored (hs/edn->hier-set with-starts? (hs/->edn original))]
    (is (instance? hier_set.core.HierSet restored))
    (is (= (seq original) (seq restored)))
    (is (= (get original "foo.bar.more")
           (get restored "foo.bar.more")))
    (is (= (hs/ancestors original "foo.bar.baz.more")
           (hs/ancestors restored "foo.bar.baz.more")))
    (is (nil? (meta restored))))
  (let [empty-set (hs/edn->hier-set with-starts?
                                   (hs/->edn (hier-set with-starts?)))]
    (is (instance? hier_set.core.HierSet empty-set))
    (is (empty? empty-set))))

(deftest test-edn-validation-and-comparator-limit
  (testing "invalid serialized values are rejected"
    (is (thrown? clojure.lang.ExceptionInfo
                 (hs/edn->hier-set with-starts? nil)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (hs/edn->hier-set with-starts? "not-edn")))
    (is (thrown? clojure.lang.ExceptionInfo
                 (hs/edn->hier-set with-starts?
                                   (pr-str {:hier-set/version 1}))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (hs/edn->hier-set with-starts?
                                   (pr-str {:hier-set/version 1
                                            :members [42]})))))
  (testing "custom comparator data remains inspectable but is not serialized"
    (let [reverse-compare #(compare %2 %1)
          custom (hier-set-by with-starts? reverse-compare "a" "b")]
      (is (= ["b" "a"] (:members (datafy/datafy custom))))
      (is (thrown? clojure.lang.ExceptionInfo (hs/->edn custom))))))

(deftest test-valid-hierarchy
  (testing "normal hierarchy instances validate"
    (is (true? (if-let [validate (ns-resolve 'hier-set.core
                                              'valid-hierarchy?)]
                 (validate (hier-set with-starts? "foo" "foo.bar" "quux"))
                 false))))
  (testing "reports an invalid parent relationship"
    (let [contents (sorted-set "foo" "foo.bar")
          corrupted (hs/->HierSet nil with-starts? contents
                                  {"foo" nil "foo.bar" "wrong-parent"})
          error (try
                  (if-let [validate (ns-resolve 'hier-set.core 'validate!)]
                    (validate corrupted)
                    (throw (ex-info "validation function is missing"
                                    {:invariant :missing-api})))
                  nil
                  (catch clojure.lang.ExceptionInfo e e))]
      (is (some? error))
      (is (re-find #"parent.*foo\.bar.*wrong-parent"
                   (.getMessage ^Throwable error)))
      (is (= :parent-index (-> error ex-data :invariant)))))
  (testing "reports descendants sorted before their ancestors"
    (let [corrupted (hier-set-by with-starts? #(compare %2 %1)
                                 "foo" "foo.bar")
          error (try
                  (if-let [validate (ns-resolve 'hier-set.core 'validate!)]
                    (validate corrupted)
                    (throw (ex-info "validation function is missing"
                                    {:invariant :missing-api})))
                  nil
                  (catch clojure.lang.ExceptionInfo e e))]
      (is (some? error))
      (is (re-find #"sort.*foo.*foo\.bar|sort.*foo\.bar.*foo"
                   (.getMessage ^Throwable error)))
      (is (= :sort-order (-> error ex-data :invariant)))))
  (testing "reports a missing containment between an ancestor and descendant"
    (let [contains? (fn [ancestor member]
                      (or (= ancestor member)
                          (and (= ancestor "a") (= member "ac"))))
          contents (sorted-set "a" "ab" "ac")
          corrupted (hs/->HierSet nil contains? contents
                                  {"a" nil "ab" nil "ac" "a"})
          error (try
                  (if-let [validate (ns-resolve 'hier-set.core 'validate!)]
                    (validate corrupted)
                    (throw (ex-info "validation function is missing"
                                    {:invariant :missing-api})))
                  nil
                  (catch clojure.lang.ExceptionInfo e e))]
      (is (some? error))
      (is (re-find #"ancestor a does not contain member ab"
                   (.getMessage ^Throwable error)))
      (is (= :containment (-> error ex-data :invariant))))))

(deftest test-set-algebra
  (let [left (hier-set with-starts? "a" "a.left" "b")
        right (hier-set with-starts? "a" "a.right" "c")]
    (testing "union returns a hierarchy-aware set in sorted order"
      (let [result (hs/union left right)]
        (is (instance? hier_set.core.HierSet result))
        (is (= '("a" "a.left" "a.right" "b" "c") (seq result)))
        (is (= '("a.right" "a") (hs/ancestors result "a.right.deep")))
        (is (= '("a" "a.left" "a.right")
               (seq (hs/descendants result "a"))))))
    (testing "intersection keeps common primary members"
      (let [result (hs/intersection left right)]
        (is (instance? hier_set.core.HierSet result))
        (is (= '("a") (seq result)))
        (is (= '("a") (hs/ancestors result "a.deep")))))
    (testing "difference removes right-hand primary members"
      (let [result (hs/difference left right)]
        (is (instance? hier_set.core.HierSet result))
        (is (= '("a.left" "b") (seq result)))
        (is (= '("a.left") (hs/ancestors result "a.left.deep")))))
    (testing "empty operands preserve the HierSet result"
      (is (instance? hier_set.core.HierSet (hs/union (empty left) right)))
      (is (= (seq left) (seq (hs/union left (empty right)))))
      (is (empty? (hs/intersection left (empty right))))
      (is (empty? (hs/difference left left)))))
  (testing "incompatible comparators are rejected"
    (let [natural (hier-set-by with-starts? compare "a")
          reverse (hier-set-by with-starts? #(compare %2 %1) "a")]
      (doseq [operation [hs/union hs/intersection hs/difference]]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"incompatible HierSet comparators"
                              (operation natural reverse)))))))

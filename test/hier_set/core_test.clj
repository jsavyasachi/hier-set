(ns hier-set.core-test
  (:require [hier-set.core :as hs])
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

(def ^:private testing-data
  ["adam" "adam.nested" "adam.nested.deeply"
   "betty"
   "david" "david.nested.deeply"
   "erin.nested"])

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
      (is (false? (.containsAll hs ["foo" "nope"]))))))

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

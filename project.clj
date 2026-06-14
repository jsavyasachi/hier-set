(defproject net.clojars.savya/hier-set "1.2.1"
  :description "A Clojure hierarchical set."
  :url "https://github.com/jsavyasachi/hier-set"
  :license {:name "Eclipse Public License 1.0"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.9.0"
  :pedantic? :warn
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.12.5"]]
  :aliases {"all" ["with-profile" ~(str "+clojure-1-10:"
                                        "+clojure-1-11:"
                                        "+clojure-1-12")]}
  :profiles {:clojure-1-10 {:dependencies
                            [[org.clojure/clojure "1.10.3"]]}
             :clojure-1-11 {:dependencies
                            [[org.clojure/clojure "1.11.4"]]}
             :clojure-1-12 {:dependencies
                            [[org.clojure/clojure "1.12.5"]]}})

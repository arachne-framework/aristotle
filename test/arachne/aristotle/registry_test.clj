(ns arachne.aristotle.registry-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle.registry :as reg])
  (:import [clojure.lang ExceptionInfo]))

(reg/alias :mike "http://example.com/people/#mike")
(deftest kw-registration
  (is (= "http://example.com/people/#mike" (reg/iri :mike))))


(reg/prefix 'foaf "http://xmlns.com/foaf/0.1/")
(deftest prefix-registration
  (is (= "http://xmlns.com/foaf/0.1/name"
        (reg/iri :foaf/name))))

(deftest fails-on-unknown-kw
  (is (thrown-with-msg? ExceptionInfo #"Could not determine IRI"
        (reg/iri :billy-bob))))

(deftest kw-generation
  (is (= :foaf/name (reg/kw "http://xmlns.com/foaf/0.1/name")))
  (is (= :mike (reg/kw "http://example.com/people/#mike")))
  (is (nil? (reg/kw "http://this-is-not-registered#foobar"))))

(deftest clojure-urns
  (is (= :foo/bar (reg/kw (reg/iri :foo/bar))))
  (is (= :datomic.api/pull (reg/kw (reg/iri :datomic.api/pull)))))


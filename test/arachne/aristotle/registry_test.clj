(ns arachne.aristotle.registry-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle.registry :as reg])
  (:import [clojure.lang ExceptionInfo]))

(deftest kw-registration
  (reg/register :mike "http://example.com/people/#mike")
  (is (= "http://example.com/people/#mike" (reg/iri :mike))))


(deftest prefix-registration
  (reg/prefix :foaf "http://xmlns.com/foaf/0.1/")
  (is (= "http://xmlns.com/foaf/0.1/name"
        (reg/iri :foaf/name))))

(deftest fails-on-unknown-kw
  (is (thrown-with-msg? ExceptionInfo #"Could not determine IRI"
        (reg/iri :no.such/registration)))

  (is (thrown-with-msg? ExceptionInfo #"Could not determine IRI"
        (reg/iri :billy-bob))))
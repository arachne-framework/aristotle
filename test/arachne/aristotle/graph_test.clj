(ns arachne.aristotle.graph-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle :as ar]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.query :as q]
            [clojure.java.io :as io]))

(reg/prefix 'foaf "http://xmlns.com/foaf/0.1/")
(reg/prefix 'test "http://example.com/aristotle#")

(deftest nested-card-many
  (let [data [{:rdf/about :test/jane
               :foaf/name "Jane"
               :foaf/knows [{:rdf/about :test/bill
                             :arachne/name "Bill"}
                            {:rdf/about :test/nicole
                             :arachne/name "Nicole"}]}]
        triples (graph/triples data)]
    (is (= 5 (count triples)))))

(deftest load-rdf-edn
  (let [g (ar/read (ar/graph :simple) (io/resource "sample.rdf.edn"))]
    (is (= #{["Jim"]}
           (q/run '[?name]
             '[:bgp
               ["<http://example.com/luke>" :foaf/knows ?person]
               [?person :foaf/name ?name]]
             g)))))

(deftest inline-prefix-test
  (let [data [#rdf/prefix [:foo "http://foo.com/#"]
              {:rdf/about :foo/luke
               :foaf/name "Luke"}]]
    (is (= #{["<http://foo.com/#luke>"]}
           (q/run '[?p]
             '[:bgp [?p :foaf/name "Luke"]]
             (ar/add (ar/graph :simple) data))))))

(reg/prefix :ex "http://example.com")

(deftest symbol-type-test
  (let [data [{:rdf/about :ex/luke
               :ex/ctor 'foo.bar/biz}]]
    (is (= #{['foo.bar/biz]}
           (q/run '[?ctor]
             '[:bgp [:ex/luke :ex/ctor ?ctor]]
             (ar/add (ar/graph :simple) data))))))


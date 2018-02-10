(ns arachne.aristotle.graph-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.query :as q]))

(reg/prefix :foaf "http://xmlns.com/foaf/0.1/")
(reg/prefix :test "http://example.org/arachne-test#")

(deftest nested-card-many
  (let [data [{:rdf/about :test/jane
               :foaf/name "Jane"
               :foaf/knows [{:rdf/about :test/bill
                             :arachne/name "Bill"}
                            {:rdf/about :test/nicole
                             :arachne/name "Nicole"}]}]
        triples (graph/triples data)]
    (is (= 5 (count triples)))))

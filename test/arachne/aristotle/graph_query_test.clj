(ns arachne.aristotle.graph-query-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.query :as query]
            [arachne.aristotle :as ar]))

(reg/prefix :foaf "http://xmlns.com/foaf/0.1/")
(reg/prefix :arachne "http://example.com/person/")

(def sample-1
  (graph/graph
    (graph/triples
      [{:rdf/about :arachne/luke
        :foaf/name "Luke"
        :foaf/age 32
        :foaf/title "Developer"
        :foaf/knows [:arachne/joe :arachne/jane]}


       {:rdf/about :arachne/joe
        :foaf/name "Joe"
        :foaf/title "Designer"
        :foaf/age 24
        :foaf/knows :arachne/jane}

       {:rdf/about :arachne/jane
        :foaf/name "Jane"
        :foaf/age 34
        :foaf/title "Developer"}])))


(deftest basic-data-and-query
  (is (= [[32]]
         (query/query '{:select [?age]
                        :where [[?luke :foaf/name "Luke"]
                                [?luke :foaf/age ?age]]}
           sample-1)))

  (is (= #{["Joe" "Jane"] ["Luke" "Jane"] ["Luke" "Joe"]}
        (set (query/query '{:select [?person-name ?knows-name]
                            :where [[?person :foaf/knows ?known]
                                    [?person :foaf/name ?person-name]
                                    [?known :foaf/name ?knows-name]]}
               sample-1)))))

(deftest constraints
  (is (= #{[:arachne/luke] [:arachne/jane]}
        (set (query/query '{:select [?person]
                            :where [[?person :foaf/age ?age]
                                    (< 30 ?age)]}
               sample-1))))

  (is (= #{[:arachne/joe]}
        (set (query/query '{:select [?person]
                            :where [[?person :foaf/age ?age]
                                    (= ?age 24)]}
               sample-1))))

  (is (= #{}
        (set (query/query '{:select [?person]
                            :where [[?person :foaf/age ?age]
                                    (< ?age 10)]}
               sample-1)))))

(ns arachne.aristotle.query-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.query :as q]
            [arachne.aristotle :as aa]
            [clojure.java.io :as io]
            [clojure.walk :as w]))

(reg/prefix 'foaf "http://xmlns.com/foaf/0.1/")
(reg/prefix 'socrata "http://www.socrata.com/rdf/terms#")
(reg/prefix 'dcat "http://www.w3.org/ns/dcat#")
(reg/prefix 'ods "http://open-data-standards.github.com/2012/01/open-data-standards#")
(reg/prefix 'dcterm "http://purl.org/dc/terms/")
(reg/prefix 'geo "http://www.w3.org/2003/01/geo/wgs84_pos#")
(reg/prefix 'skos "http://www.w3.org/2004/02/skos/core#")
(reg/prefix 'dsbase "http://data.lacity.org/resource/")
(reg/prefix 'ds "https://data.lacity.org/resource/zzzz-zzzz/")

(def test-graph (aa/read (aa/graph :simple) (io/resource "la_census.rdf")))

(deftest basic-query
  (is (= #{["57110"]}
         (q/run test-graph '[?pop]
           '[:bgp {:rdf/about ?e
                   :ds/zip_code "90001"
                   :ds/total_population ?pop}])))
  (is (= #{["57110"]}
         (q/run test-graph '[?pop]
           '[:bgp
             [?e :ds/zip_code "90001"]
             [?e :ds/total_population ?pop]])))
  (let [results (q/run test-graph
                  '[:bgp
                    [?e :ds/zip_code "90001"]
                    [?e :ds/total_population ?pop]])]
    (is (= "57110" (get (first results) '?pop)))))

(deftest functions+filters
  (is (= #{["90650"]}
         (q/run test-graph '[?zip]
           '[:filter (< 105000 (:xsd/integer ?pop))
             [:bgp
              [?e :ds/zip_code ?zip]
              [?e :ds/total_population ?pop]]]))))

(deftest aggregates
  (is (= #{[319 0 105549 33241]}
         (q/run test-graph '[?count ?min ?max ?avg]
           '[:extend [?avg (round ?avgn)]
             [:group [] [?count (count)
                         ?min (min (:xsd/integer ?pop))
                         ?max (max (:xsd/integer ?pop))
                         ?avgn (avg (:xsd/integer ?pop))]
              [:bgp
               [_ :ds/total_population ?pop]]]]))))

(deftest minus
  (is (= 5 (count (q/run test-graph
                    '[:diff
                      [:bgp [?zip :ds/total_population "0"]]
                      [:bgp [?zip :ds/zip_code "90831"]]])))))

(deftest unions
  (is (= 2 (count (q/run test-graph
                    '[:union
                      [:bgp [?zip :ds/zip_code "92821"]]
                      [:bgp [?zip :ds/zip_code "90831"]]])))))


(reg/prefix 'foaf "http://xmlns.com/foaf/0.1/")
(reg/prefix 'test "http://example.com/aristotle#")

(def ca-graph (-> (aa/graph :simple) (aa/add [{:rdf/about :test/olivia
                                               :foaf/name "Olivia Person"
                                               :foaf/title "Dr"}
                                              {:rdf/about :test/frank
                                               :foaf/name "Frank Person"
                                               :foaf/title "Dr"}
                                              {:rdf/about :test/jenny
                                               :foaf/name "Jenny Person"}
                                              {:rdf/about :test/sophia
                                               :foaf/name "Sophie Person"
                                               :foaf/title "Commander"}])))

(deftest count-aggregates
  (is (= #{[4 3 4 2]}
         (q/run ca-graph
           '[?simple-count ?title-count ?distinct-count ?distinct-title-count]
           '[:group [] [?simple-count (count)
                        ?title-count (count ?title)
                        ?distinct-count (count (distinct))
                        ?distinct-title-count (count (distinct ?title))]
             [:conditional
              [:bgp [?p :foaf/name ?name]]
              [:bgp [?p :foaf/title ?title]]]]))))

(deftest query-parameters
  (testing "single var, single value"
    (is (= #{["90001" "57110"]}
           (q/run test-graph '[?zip ?pop]
             '[:bgp
               [?e :ds/zip_code ?zip]
               [?e :socrata/rowID ?id]
               [?e :ds/total_population ?pop]
               [?e ?a ?v]]
             {'?zip "90001"}))))

  (testing "single var, multiple values."
    (is (= #{["90001" "57110"]
             ["90005" "37681"]}
           (q/run test-graph '[?zip ?pop]
             '[:bgp
               [?e :ds/zip_code ?zip]
               [?e :socrata/rowID ?id]
               [?e :ds/total_population ?pop]
               [?e ?a ?v]]
             {'?zip ["90001" "90005"]}))))

  (testing "multiple vars, single values."
    (is (= #{}
           (q/run test-graph
             '[?pop] '[:bgp
                       [?e :ds/zip_code ?zip]
                       [?e :socrata/rowID ?id]
                       [?e :ds/total_population ?pop]
                       [?e ?a ?v]]
             {'?zip "90001"
              '?id "228"}))))

  (testing "multiple vars, multiple values"
    (is (= #{["51223"]}
           (q/run test-graph
             '[?pop] '[:bgp
                       [?e :ds/zip_code ?zip]
                       [?e :socrata/rowID ?id]
                       [?e :ds/total_population ?pop]
                       [?e ?a ?v]]
             {'?zip ["90001" "90002"]
              '?id ["2" "3"]}))))

  (testing "relational values"
    (is (= #{["57110"]}
           (q/run test-graph
             '[?pop] '[:bgp
                       [?e :ds/zip_code ?zip]
                       [?e :socrata/rowID ?id]
                       [?e :ds/total_population ?pop]
                       [?e ?a ?v]]
             {'[?zip ?id] [["90001" "1"]
                           ["90002" "0"]]}))))

  (testing "relational values, some unbound"
    (is (= #{["57110"]
             ["51223"]}
           (q/run test-graph '[?pop]
             '[:bgp
               [?e :ds/zip_code ?zip]
               [?e :socrata/rowID ?id]
               [?e :ds/total_population ?pop]
               [?e ?a ?v]]
             {'[?zip ?id] [["90001" "1"]
                           ["90002" nil]]})))))

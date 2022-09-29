(ns arachne.aristotle.graph-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle :as ar]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.query :as q]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(reg/prefix 'foaf "http://xmlns.com/foaf/0.1/")
(reg/prefix 'test "http://example.com/test/")
(reg/prefix 'arachne "http://arachne-framework.org/#")

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
           (q/run g '[?name]
             '[:bgp
               ["<http://example.com/luke>" :foaf/knows ?person]
               [?person :foaf/name ?name]])))))

(defn- entity-with-name
  [data name]
  (ffirst
    (q/run
      (ar/add (ar/graph :simple) data)
      '[?p]
      '[:bgp [?p :foaf/name ?name]]
      {'?name name})))

(deftest inline-prefix-test
  (let [str "[#rdf/prefix [:foo \"http://foo.com/#\"]
              {:rdf/about :foo/luke
               :foaf/name \"Luke\"}]"
        data (edn/read-string {:readers *data-readers*} str)]
    (is (= "<http://foo.com/#luke>" (entity-with-name data "Luke")))))

(deftest global-prefix-test
  (testing "initial usage"
    (let [str "[#rdf/global-prefix [:baz \"http://baz.com/#\"]
                {:rdf/about :baz/luke
                 :foaf/name \"Luke\"}]"
          data (edn/read-string {:readers *data-readers*} str)]
      (is (= :baz/luke (entity-with-name data "Luke")))))
  (testing "subsequent usage"
    (let [data {:rdf/about :baz/jim
                :foaf/name "Jim"}]
      (is (= :baz/jim (entity-with-name data "Jim"))))
    (is (reg/prefix :baz "http://baz.com/#")))
  (testing "conflict"
    (is (thrown-with-msg? Exception #"namespace is already registered"
          (reg/prefix :baz "http://bazbazbaz.com/#")))
    (is (thrown-with-msg? Exception #"namespace is already registered"
          (edn/read-string {:readers *data-readers*}
            "#rdf/global-prefix [:baz \"http://bazbazbaz.com/#\"]")))))

(reg/prefix :ex "http://example2.com")

(deftest symbol-type-test
  (let [data [{:rdf/about :ex/luke
               :ex/ctor 'foo.bar/biz}]]
    (is (= #{['foo.bar/biz]}
           (q/run
             (ar/add (ar/graph :simple) data)
             '[?ctor]
             '[:bgp [:ex/luke :ex/ctor ?ctor]])))))

(deftest reverse-keyword-test
  (let [data [{:rdf/about :test/luke
               :foaf/_knows :test/jon}
              {:rdf/about :test/hannah
               :foaf/_knows [{:rdf/about :test/luke}]}]
        g (ar/add (ar/graph :simple) data)]
    (is (= #{[:test/jon :test/luke]
             [:test/luke :test/hannah]}
           (q/run g '[?a ?b]
             '[:bgp [?a :foaf/knows ?b]])))))

(deftest empty-map-vals
  (let [data [{:rdf/about :test/luke
               :rdf/name "Luke"
               :foaf/knows [nil]}]]
    (is (= 1 (count (graph/triples data))))))

(deftest rdf-linked-lists
  "Convert Clojure lists to RDF linked lists"
  (let [data [{:rdf/about :test/race
               :test/starters (graph/rdf-list [:test/luke :test/stuart :test/joe])}]
        g (ar/add (ar/graph :simple) data)]
    (testing "RDF list triples"
      (is (not (empty?
                 (q/run g '[?l0 ?l1 ?l2]
                   '[:bgp
                     [:test/race :test/starters ?l0]
                     [?l0 :rdf/first :test/luke]
                     [?l0 :rdf/rest ?l1]
                     [?l1 :rdf/first :test/stuart]
                     [?l1 :rdf/rest ?l2]
                     [?l2 :rdf/first :test/joe]
                     [?l2 :rdf/rest :rdf/nil]])))))))

(deftest rdf-containers
  "Conver Clojure data to RDF containers"
  (let [data [{:rdf/about :test/groceries
               :test/contents (graph/rdf-bag [:test/apple :test/banana :test/orange])}]
        g (ar/add (ar/graph :simple) data)]
    (testing "RDF bag triples"
      (is (not (empty?
                 (q/run g '[?l0 ?l1 ?l2]
                   '[:bgp
                     [:test/groceries :test/contents ?bag]
                     [?bag :rdf/_0 :test/apple]
                     [?bag :rdf/_1 :test/banana]
                     [?bag :rdf/_2 :test/orange]])))))))


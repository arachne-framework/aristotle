(ns arachne.aristotle.reification-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle :as aa]))

(reg/prefix 'foaf "http://xmlns.com/foaf/0.1/")

(deftest reification-test
  (let [g (aa/add (aa/graph :simple) {:rdf/about "<http://example.com/#luke>"
                                      :foaf/name "Luke"
                                      :foaf/knows {:rdf/about "<http://example.com/#Stu>"
                                                   :foaf/name "Stuart"}})]

    (is (= 3 (count (graph/triples g))))
    (let [g (graph/reify g "<http://example.com/graph>" "<http://example.com/graph1>")]
      (is (= 18 (count (graph/triples g)))))))

(comment
  ;; Reification Benchmarking

  (import '[java.util UUID])

  (def entities (vec (repeatedly 5000 (fn []
                                         (str "<http://example.com/" (UUID/randomUUID) ">")))))


  (def properties (vec (repeatedly 500 (fn []
                                           (str "<http://example.com/p/" (UUID/randomUUID) ">")))))


  (defn rand-triple
    []
    [(rand-nth entities) (rand-nth properties) (case (rand-int 3)
                                                 0 (rand-nth entities)
                                                 1 (rand)
                                                 2 (str (UUID/randomUUID)))])

  (def n 100000)

  (time
   (let [g (aa/add (aa/graph :jena-mini) (repeatedly n rand-triple))
         g (graph/reify g "<http://example.com/graph>" (str (UUID/randomUUID)))]
     (def the-g g)))


  (def the-g nil)

  (time (count (graph/triples the-g))))

  ;; Results: 100k triples (before reification) in a :jena-mini graph
  ;; cost about 1.1G of heap.

  ;; :simple is much cheaper, can fit about 1M
  ;; triples (before reification) in a 2GB data structure.




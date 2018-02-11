(ns arachne.aristotle.inference-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle :as aa]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.inference :as inf]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.query :as q]
            [arachne.aristotle :as ar]
            [clojure.java.io :as io]))

(reg/prefix 'daml "http://www.daml.org/2001/03/daml+oil#")
(reg/prefix 'wo.tf "http://www.workingontologist.org/Examples/Chapter6/TheFirm.owl#")
(reg/prefix 'arachne "http://arachne-framework.org/#")

(deftest basic-type-inference
  (let [m (aa/add (aa/model :jena-mini) (graph/load (io/resource "TheFirm.n3")))
        gls #{[:wo.tf/Goldman]
              [:wo.tf/Long]
              [:wo.tf/Spence]}
        withsmith (conj gls [:arachne/Smith])
        ppl-query '[:project [?person]
                    [:bgp
                     [?person :rdf/type :wo.tf/Person]]]
        worksfor-query '[:project [?person]
                         [:bgp
                          [?person :wo.tf/worksFor :wo.tf/TheFirm]]]]
    (is (= gls (set (q/query ppl-query m))))
    (is (= gls (set (q/query worksfor-query m))))
    (aa/add m {:rdf/about :arachne/Smith
               :wo.tf/freeLancesTo :wo.tf/TheFirm})
    (is (= withsmith (set (q/query ppl-query m))))
    (is (= withsmith (set (q/query worksfor-query m))))))

(def pres-props
  [{:rdf/about :wo.tf/president
    :owl/class :owl/FunctionalProperty
    :rdfs/domain :wo.tf/Company
    :rdfs/range :wo.tf/Person
    :owl/inverseOf :wo.tf/presidentOf}
   {:rdf/about :wo.tf/presidentOf
    :rdfs/subPropertyOf :wo.tf/isEmployedBy}
   {:rdf/about :wo.tf/TheFirm
    :wo.tf/president :wo.tf/Flint}])

(deftest inverse-properties
  (let [m (aa/add (aa/model :jena-mini) (graph/load (io/resource "TheFirm.n3")))]
    (aa/add m pres-props)
    (is
     (= [[:wo.tf/TheFirm]]
        (q/query '[:project [?firm]
                   [:bgp
                    [:wo.tf/Flint :wo.tf/worksFor ?firm]]] m)))))

(def custom-ruleset
  [(inf/rule :body '[[?thing :arachne/eats ?food]
                     [?food :rdf/type :arachne/Animal]]
             :head '[[?thing :arachne/carnivore true]])])

(deftest custom-rules
  (let [m (aa/add (aa/model :jena-rules (concat inf/owl-rules custom-ruleset))
                  [{:rdf/about :arachne/leo
                    :arachne/name "Leo"
                    :arachne/eats :arachne/jumper}
                   {:rdf/about :arachne/jumper
                    :rdf/type :arachne/Gazelle}
                   {:rdf/about :arachne/Gazelle
                    :rdfs/subClassOf :arachne/Animal}])]
    (is (= [[:arachne/leo]]
           (q/query '[:project [?e]
                      [:bgp
                       [?e :arachne/carnivore true]]] m)))))

(deftest functional-properties
  (let [m (aa/add (aa/model :jena-mini)
                  [{:rdf/about :arachne/legalSpouse
                    :rdf/type [:owl/ObjectProperty :owl/FunctionalProperty]
                    :rdfs/domain :arachne/Person
                    :rdfs/range :arachne/Person}
                   {:rdf/about :arachne/jon
                    :arachne/name "John"
                    :arachne/legalSpouse [{:rdf/about :arachne/will
                                           :arachne/name "William"}]}
                   {:rdf/about :arachne/jon
                    :arachne/legalSpouse [{:rdf/about :arachne/bill
                                           :arachne/name "Bill"}]}])]

    (q/query '[:project [?b]
               [:bgp
                [?b :arachne/name "William"]
                [?b :arachne/name "Bill"]]] m)))







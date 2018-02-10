(ns arachne.aristotle.validation-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle :as aa]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.query :as q]
            [arachne.aristotle.validation :as v]
            [arachne.aristotle :as ar]
            [clojure.java.io :as io]))

(reg/prefix :daml "http://www.daml.org/2001/03/daml+oil#")
(reg/prefix :wo.tf "http://www.workingontologist.org/Examples/Chapter6/TheFirm.owl#")
(reg/prefix :arachne "http://arachne-framework.org/#")

(deftest disjoint-classes
  (let [m (aa/add (aa/model :jena-owl) (graph/load (io/resource "TheFirm.n3")))]
    (aa/add m {:rdf/about :wo.tf/TheFirm
               :wo.tf/freeLancesTo :wo.tf/TheFirm})

    (is (empty? (v/validate m)))

    (aa/add m {:rdf/about :wo.tf/Company
               :owl/disjointWith :wo.tf/Person})

    (let [errors (v/validate m)]
      (is (= 2 (count errors)))
      (is (re-find #"disjoint" (::v/description (first errors))))
      (is (re-find #"same and different" (::v/description (second errors)))))))

(def pres-props
  [{:rdf/about :wo.tf/president
    :rdf/type :owl/FunctionalProperty
    :rdfs/domain :wo.tf/Company
    :rdfs/range :wo.tf/Person
    :owl/inverseOf :wo.tf/presidentOf}
   {:rdf/about :wo.tf/presidentOf
    :rdfs/subPropertyOf :wo.tf/isEmployedBy}
   {:rdf/about :wo.tf/TheFirm
    :wo.tf/president :wo.tf/Flint}])

(deftest functional-object-properties
  (let [m (aa/add (aa/model :jena-owl)
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
                                           :arachne/name "Bill"
                                           :owl/differentFrom :arachne/will}]}])]
    (let [errors (v/validate m)]
      (is (not (empty? errors)))
      (is (some #(re-find #"too many values" (::v/jena-type %)) errors)))))

(deftest functional-datatype-properties
  (let [m (aa/add (aa/model :jena-owl)
                  [{:rdf/about :arachne/name
                    :rdf/type [:owl/DatatypeProperty :owl/FunctionalProperty]
                    :rdfs/domain :arachne/Person
                    :rdfs/range :arachne/Person}
                   {:rdf/about :arachne/jon
                    :arachne/name #{"John" "Jeff"}}])]
    (let [errors (v/validate m)]
      (is (not (empty? errors)))
      (is (some #(re-find #"too many values" (::v/jena-type %)) errors)))))

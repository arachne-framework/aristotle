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
  (let [m (aa/add (aa/model) (graph/load (io/resource "TheFirm.n3")))]
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
    :owl/class :owl/FunctionalProperty
    :rdfs/domain :wo.tf/Company
    :rdfs/range :wo.tf/Person
    :owl/inverseOf :wo.tf/presidentOf}
   {:rdf/about :wo.tf/presidentOf
    :rdfs/subPropertyOf :wo.tf/isEmployedBy}
   {:rdf/about :wo.tf/TheFirm
    :wo.tf/president :wo.tf/Flint}])

(deftest functional-properties
  (let [m (aa/add (aa/model) (graph/load (io/resource "TheFirm.n3")))]
    (aa/add m pres-props)

    (is (empty? (v/validate m)))

    (aa/add m {:rdf/about :wo.tf/Obsidian
               :wo.tf/presidentOf :wo.tf/TheFirm})

    (let [errors (v/validate m)]
      (is (= 1 (count errors)))
      (is (re-find #"Functional property violation" (::v/description (first errors)))))


    )
  )


;; TODO: write validators & tests for

;; inverse functional properties
;; cardinality

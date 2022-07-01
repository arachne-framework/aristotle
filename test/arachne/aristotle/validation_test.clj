(ns arachne.aristotle.validation-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle :as aa]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.validation :as v]
            [clojure.java.io :as io]))

(reg/prefix 'daml "http://www.daml.org/2001/03/daml+oil#")
(reg/prefix 'wo.tf "http://www.workingontologist.org/Examples/Chapter6/TheFirm.owl#")
(reg/prefix 'arachne "http://arachne-framework.org/#")

(deftest disjoint-classes
  (let [g (aa/read (aa/graph :jena-mini) (io/resource "TheFirm.n3"))
        g (aa/add g {:rdf/about :wo.tf/TheFirm
                     :wo.tf/freeLancesTo :wo.tf/TheFirm})]

    (is (empty? (v/validate g)))



    (let [g (aa/add g {:rdf/about :wo.tf/Company
                       :owl/disjointWith :wo.tf/Person})
          errors (v/validate g)]
      (is (= 2 (count errors)))
      (is (re-find #"disjoint" (::v/description (first errors))))
      (is (re-find #"same and different" (::v/description (second errors)))))))

(deftest functional-object-properties
  (let [g (aa/add (aa/graph :jena-mini)
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
    (let [errors (v/validate g)]
      (is (not (empty? errors)))
      (is (some #(re-find #"too many values" (::v/jena-type %)) errors)))))

(deftest functional-datatype-properties
  (let [g (aa/add (aa/graph :jena-mini)
                  [{:rdf/about :arachne/name
                    :rdf/type [:owl/DatatypeProperty :owl/FunctionalProperty]
                    :rdfs/domain :arachne/Person
                    :rdfs/range :xsd/string}
                   {:rdf/about :arachne/jon
                    :arachne/name #{"John" "Jeff"}}])]
    (let [errors (v/validate g)]
      (is (not (empty? errors)))
      (is (some #(re-find #"too many values" (::v/jena-type %)) errors)))))

(deftest max-cardinality-datatype
  (let [g (aa/add (aa/graph :jena-mini)
                  [{:rdf/about :arachne/Person
                    :rdfs/subClassOf {:rdf/type :owl/Restriction
                                      :owl/onProperty :arachne/name
                                      :owl/maxCardinality 2}}
                   {:rdf/about :arachne/name
                    :rdf/type [:owl/DatatypeProperty]
                    :rdfs/domain :arachne/Person
                    :rdfs/range :xsd/string}
                   {:rdf/about :arachne/jon
                    :arachne/name #{"John" "Jeff" "James"}}])]
    (let [errors (v/validate g)]
      (is (not (empty? errors)))
      (is (some #(re-find #"too many values" (::v/jena-type %)) errors)))))

(deftest max-cardinality-object
  (testing "max 1"
    (let [g (aa/add (aa/graph :jena-mini)
                    [{:rdf/about :arachne/Person
                      :rdfs/subClassOf {:rdf/type :owl/Restriction
                                        :owl/onProperty :arachne/friends
                                        :owl/maxCardinality 1}}
                     {:rdf/about :arachne/friends
                      :rdf/type [:owl/ObjectProperty]
                      :rdfs/domain :arachne/Person
                      :rdfs/range :arachne/Person}
                     {:rdf/about :arachne/jon
                      :arachne/name "John"
                      :arachne/friends #{{:rdf/about :arachne/jeff
                                          :arachne/name "Jeff"
                                          :owl/differentFrom :arachne/jim}
                                         {:rdf/about :arachne/jim
                                          :arachne/name "James"}}}])]
      (let [errors (v/validate g)]
        (is (not (empty? errors)))
        (is (some #(re-find #"too many values" (::v/jena-type %)) errors))))
    (testing "max N"
      (let [g (aa/add (aa/graph :jena-mini)
                      [{:rdf/about :arachne/Person
                        :rdfs/subClassOf {:rdf/type :owl/Restriction
                                          :owl/onProperty :arachne/friends
                                          :owl/maxCardinality 2}}
                       {:rdf/about :arachne/friends
                        :rdf/type [:owl/ObjectProperty]
                        :rdfs/domain :arachne/Person
                        :rdfs/range :arachne/Person}
                       {:rdf/about :arachne/jon
                        :arachne/name "John"
                        :arachne/friends #{{:rdf/about :arachne/jeff
                                            :arachne/name "Jeff"
                                            :owl/differentFrom [:arachne/jim :arachne/sara]}
                                           {:rdf/about :arachne/jim
                                            :arachne/name "James"
                                            :owl/differentFrom [:arachne/sara :arachne/jeff]}
                                           {:rdf/about :arachne/sara
                                            :arachne/name "Sarah"
                                            :owl/differentFrom [:arachne/jim :arachne/jeff]}}}])]

        (let [errors (v/validate g)]
          ;; The reasoner doesn't support this currently and there isn't a
          ;; great way to write a query, so we'll do without
          (is (empty? errors)))))))

(deftest min-cardinality
  (let [schema [{:rdf/about :arachne/Person
                 :rdfs/subClassOf [{:rdf/type :owl/Restriction
                                    :owl/onProperty :arachne/name
                                    :owl/cardinality 1}
                                   {:rdf/type :owl/Restriction
                                    :owl/onProperty :arachne/friends
                                    :owl/minCardinality 2}]}
                {:rdf/about :arachne/name
                 :rdf/type [:owl/DatatypeProperty]
                 :rdfs/domain :arachne/Person
                 :rdfs/range :xsd/string}
                {:rdf/about :arachne/friends
                 :rdf/type [:owl/ObjectProperty]
                 :rdfs/domain :arachne/Person
                 :rdfs/range :arachne/Person}]]

    (let [g (-> (aa/graph :jena-mini) (aa/add schema)
                (aa/add [{:rdf/about :arachne/jon
                          :arachne/name "John"
                          :arachne/friends {:rdf/about :arachne/nicole}}]))]

      (let [errors (v/validate g [v/min-cardinality])]
        (is (= 3 (count errors)))
        (is (= #{::v/min-cardinality} (set (map ::v/type errors))))
        (is (= {:arachne/name 1
                :arachne/friends 2} (frequencies
                                     (map (comp :property ::v/details) errors))))
        (is (= {:arachne/jon 1
                :arachne/nicole 2} (frequencies
                                    (map (comp :entity ::v/details) errors))))))))

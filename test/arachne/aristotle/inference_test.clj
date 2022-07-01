(ns arachne.aristotle.inference-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle :as aa]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.inference :as inf]
            [arachne.aristotle.query :as q]
            [clojure.java.io :as io]))

(reg/prefix 'daml "http://www.daml.org/2001/03/daml+oil#")
(reg/prefix 'wo.tf "http://www.workingontologist.org/Examples/Chapter6/TheFirm.owl#")
(reg/prefix :arachne "http://arachne-framework.org/#")

(reg/prefix (ns-name *ns*) "http://example.com/#")

(deftest basic-type-inference
  (let [g (aa/read (aa/graph :jena-mini)
                   (io/resource "TheFirm.n3"))
        gls #{[:wo.tf/Goldman]
              [:wo.tf/Long]
              [:wo.tf/Spence]}
        withsmith (conj gls [:arachne/Smith])
        ppl-query '[:bgp
                    [?person :rdf/type :wo.tf/Person]]
        worksfor-query '[:bgp
                         [?person :wo.tf/worksFor :wo.tf/TheFirm]]]
    (is (= gls (set (q/run g '[?person] ppl-query))))
    (is (= gls (set (q/run g '[?person] worksfor-query))))
    (let [g (aa/add g {:rdf/about :arachne/Smith
                       :wo.tf/freeLancesTo :wo.tf/TheFirm})]
      (is (= withsmith (set (q/run g '[?person] ppl-query))))
      (is (= withsmith (set (q/run g '[?person] worksfor-query)))))))

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
  (let [g (aa/read (aa/graph :jena-mini) (io/resource "TheFirm.n3"))
        g (aa/add g pres-props)]
    (is
     (= #{[:wo.tf/TheFirm]}
        (q/run g '[?firm]
          '[:bgp
            [:wo.tf/Flint :wo.tf/worksFor ?firm]])))))

(def custom-ruleset
  [(inf/rule :body '[[?thing :arachne/eats ?food]
                     [?food :rdf/type :arachne/Animal]]
             :head '[[?thing :arachne/carnivore true]])])

(deftest custom-rules
  (let [g (aa/add (aa/graph :jena-rules (concat inf/owl-rules custom-ruleset))
                  [{:rdf/about :arachne/leo
                    :arachne/name "Leo"
                    :arachne/eats :arachne/jumper}
                   {:rdf/about :arachne/jumper
                    :rdf/type :arachne/Gazelle}
                   {:rdf/about :arachne/Gazelle
                    :rdfs/subClassOf :arachne/Animal}])]
    (is (= #{[:arachne/leo]}
           (q/run g '[?e] '[:bgp
                            [?e :arachne/carnivore true]])))))

(deftest functional-properties
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
                                           :arachne/name "Bill"}]}])]

    (is (= #{[:arachne/will] [:arachne/bill]}
           (q/run g '[?b]
             '[:bgp
               [?b :arachne/name "William"]
               [?b :arachne/name "Bill"]])))))

(reg/prefix :foaf "http://xmlns.com/foaf/0.1/")
(reg/prefix :dc "http://purl.org/dc/elements/1.1/")

(deftest custom-forward-rules
  (let [inverse-rule (inf/rule :body '[?p :owl/inverseOf ?q]
                               :head (inf/rule :body '[?y ?q ?x]
                                               :head '[?x ?p ?y])
                               :dir :forward)
        knows-rule (inf/rule :body '[[?a :foaf/made ?work]
                                     [?b :foaf/made ?work]]
                             :head '[?a :foaf/knows ?b])
        g (aa/graph :jena-rules [inf/table-all inverse-rule knows-rule])
        g (aa/read g (io/resource "foaf.rdf"))
        g (aa/add g [{:rdf/about ::practical-clojure
                      :dc/title "Practical Clojure"
                      :foaf/maker [::luke
                                   ::stuart]}])]
    (is (= #{[::stuart]}
           (q/run g '[?s]
             '[:filter (not= ::luke ?s)
               [:bgp [::luke :foaf/knows ?s]]])))))

(deftest dynamic-rules
  (let [g (aa/graph :jena-rules [])
        g (aa/read g (io/resource "foaf.rdf"))
        g (aa/add g [{:rdf/about ::practical-clojure
                      :dc/title "Practical Clojure"
                      :foaf/maker [::luke
                                   ::stuart]}])
        g (inf/add g inf/mini-rules)]
    (q/run g '[?a]
      '[:bgp [?a :rdf/type :foaf/Agent]])))




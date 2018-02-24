(ns arachne.aristotle.inference-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle :as aa]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.inference :as inf]
            [arachne.aristotle.query :as q]
            [arachne.aristotle :as ar]
            [clojure.java.io :as io]))

(reg/prefix 'daml "http://www.daml.org/2001/03/daml+oil#")
(reg/prefix 'wo.tf "http://www.workingontologist.org/Examples/Chapter6/TheFirm.owl#")
(reg/prefix 'arachne "http://arachne-framework.org/#")
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
    (is (= gls (set (q/run '[?person] ppl-query g))))
    (is (= gls (set (q/run '[?person] worksfor-query g))))
    (let [g (aa/add g {:rdf/about :arachne/Smith
                       :wo.tf/freeLancesTo :wo.tf/TheFirm})]
      (is (= withsmith (set (q/run '[?person] ppl-query g))))
      (is (= withsmith (set (q/run '[?person] worksfor-query g)))))))

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
        (q/run '[?firm]
          '[:bgp
            [:wo.tf/Flint :wo.tf/worksFor ?firm]] g)))))

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
           (q/run '[?e] '[:bgp
                          [?e :arachne/carnivore true]] g)))))

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
           (q/run '[?b]
             '[:bgp
               [?b :arachne/name "William"]
               [?b :arachne/name "Bill"]]
             g)))))

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
           (q/run '[?s]
             '[:filter (not= ::luke ?s)
               [:bgp [::luke :foaf/knows ?s]]]
             g)))))

#_(deftest dynamic-rules
  (let [m (aa/graph :jena-rules [])]

    (inf/add m inf/mini-rules)

    (println "rebinding")
    (.reset m)
    (.rebind m)
    (.prepare m)

    (aa/read m (io/resource "foaf.rdf"))
    (aa/add m [{:rdf/about ::practical-clojure
                :dc/title "Practical Clojure"
                :foaf/maker [::luke
                             ::stuart]}])
    #_(is (empty? (q/run '[?a]
                  '[:bgp [?a :rdf/type :foaf/Agent]]
                  m)))


    (q/run '[?a]
      '[:bgp [?a :rdf/type :foaf/Agent]]
      m)

    ;(.getGraph m)
    ;(count (iterator-seq (.listStatements (.getRawModel m))))
    ;(class (.getGraph (.getRawModel m)))

    )


  )






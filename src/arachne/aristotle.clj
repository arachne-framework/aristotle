(ns arachne.aristotle
  "Primary API"
  (:require [arachne.aristotle.graph :as g]
            [arachne.aristotle.query :as q]
            [arachne.aristotle.registry :as reg]
            [clojure.java.io :as io])
  (:import [org.apache.jena.rdf.model ModelFactory Model]
           [org.apache.jena.ontology OntModelSpec]
           [org.apache.jena.reasoner.rulesys GenericRuleReasoner]
           [org.apache.jena.graph Factory]
           [org.apache.jena.rdf.model.impl InfModelImpl]))


(defmulti model
  "Build a new, empty model of the specified type.

   Built-in types are:

   :simple - A basic in-memory RDF store with no reasoner.
   :jena-mini - Jena's partial implementation of OWL Full with
                an in-memory store (.
   :jena-rules - Jena's GenericRuleReasoner. Takes a second argument,
                 which is a collection of rules to use (see
                 arachne.aristotle.inference for tools to create
                 rules, and some pre-built rulesets.)"
  (fn [type & _] type))

(defmethod model :simple
  [_]
  (ModelFactory/createModelForGraph (Factory/createGraphMem)))

(defmethod model :jena-mini
  [_]
  (ModelFactory/createOntologyModel OntModelSpec/OWL_MEM_MINI_RULE_INF))

(defmethod model :jena-rules
  [_ rules]
  (let [reasoner (GenericRuleReasoner. rules)]
     (.setOWLTranslation reasoner true)
     (.setTransitiveClosureCaching reasoner true)
     (InfModelImpl. (.bind reasoner (Factory/createGraphMem)))))

(defn add
  "Add the given triples to the specified model. Triples may be
   specified as Clojure data structures, Jena graph objects, or resolvable
   URL/URIs containing RDF data"
  [^Model model & triples]
  (doseq [triple (mapcat g/triples triples)]
    (.add model (.asStatement model triple)))
  model)

;; TODO s
;; 1. Add scoped registries
;; 2. add *.rdf.edn extension, registr with Aristotle
;; 3. profit...?

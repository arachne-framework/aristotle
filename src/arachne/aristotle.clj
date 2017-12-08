(ns arachne.aristotle
  "Primary API"
  (:require [arachne.aristotle.graph :as g]
            [arachne.aristotle.query :as q]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [clojure.java.io :as io])
  (:import [org.apache.jena.rdf.model ModelFactory Model]
           [org.apache.jena.ontology OntModelSpec]))

(defn model
  "Build a new, empty ontology model."
  []
  (ModelFactory/createOntologyModel OntModelSpec/OWL_MEM_RULE_INF))

(defn add
  "Add the given triples to the specified ontology model. Triples may be
   specified as Clojure data structures, Jena graph objects, or resolvable
   URL/URIs containing RDF data"
  [^Model model & triples]
  (doseq [triple (mapcat graph/triples triples)]
    (.add model (.asStatement model triple)))
  model)

;; TODO s
;; 1. Add scoped registries
;; 2. add *.rdf.edn extension, registr with Aristotle
;; 3. profit...?
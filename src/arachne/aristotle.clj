(ns arachne.aristotle
  "Primary API"
  (:require [arachne.aristotle.graph :as g]
            [arachne.aristotle.query :as q]
            [arachne.aristotle.locks :as l]
            [arachne.aristotle.registry :as reg]
            [clojure.java.io :as io])
  (:import [org.apache.jena.rdf.model ModelFactory Model]
           [org.apache.jena.ontology OntModelSpec]
           [org.apache.jena.reasoner.rulesys GenericRuleReasoner]
           [org.apache.jena.graph Factory]
           [org.apache.jena.rdf.model.impl InfModelImpl]
           [org.apache.jena.riot RDFDataMgr]))

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
  "Add the given data to a model. Valid types include:

  - String URIs, URIs, URLs or java.io.File objects represention
    locations from which to load serialized RDF.
  - Data satisfying arachne.aristotle.graph/AsTriples"
  [model data]
  (l/write model
    (cond
      (string? data) (RDFDataMgr/read ^Model model ^String data)
      (uri? data) (add model (str data))
      (instance? java.net.URL data) (RDFDataMgr/read model (str (.toURI data)))
      (instance? java.io.File data) (RDFDataMgr/read model
                                                     (-> data
                                                         (.getAbsoluteFile)
                                                         (.toURI)
                                                         (str)))
      :else (let [triples (g/triples data)]
              (doseq [triple triples]
                (.add model (.asStatement model triple))))))
  model)


(ns arachne.aristotle
  "Primary API"
  (:require [arachne.aristotle.graph :as g]
            [arachne.aristotle.query :as q]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.inference :as inf]
            [arachne.aristotle.rdf-edn]
            [clojure.java.io :as io])
  (:import [org.apache.jena.reasoner.rulesys GenericRuleReasoner]
           [org.apache.jena.graph Factory Graph GraphUtil]
           [org.apache.jena.riot RDFDataMgr]
           [java.net URL]
           [java.io File])
  (:refer-clojure :exclude [read]))

(defmulti graph
  "Build a new, empty graph of the specified type.

   Built-in types are:

   :simple - A basic in-memory RDF graph with no reasoner.
   :jena-mini - Jena's partial implementation of OWL Full with
                an in-memory store..
   :jena-rules - Jena's GenericRuleReasoner. Takes a second argument,
                 which is a collection of rules to use (see
                 arachne.aristotle.inference for tools to create
                 rules and some pre-built rulesets.)"
  (fn [type & _] type))

(defmethod graph :simple
  [_]
  (Factory/createGraphMem))

(defmethod graph :jena-mini
  [_]
  (graph :jena-rules inf/mini-rules))

;; Note: You'll probably want to include the basic tabling rule to
;; avoid infinite lookups on recursive backchains

(defmethod graph :jena-rules
  [_ initial-rules]
  (let [reasoner (GenericRuleReasoner. initial-rules)]
     (.setOWLTranslation reasoner true)
     (.setTransitiveClosureCaching reasoner true)
     (.bind reasoner (Factory/createGraphMem))))

(defn add
  "Add the given data to a graph, returning the graph. Data must satisfy
  arachne.aristotle.graph/AsTriples. If the data is a Graph it will be
  added directly."
  [graph data]
  (if (instance? Graph data)
    (GraphUtil/addInto ^Graph graph ^Graph data)
    (GraphUtil/add ^Graph graph ^java.util.List (g/triples data)))
  graph)

(defn read
  "Load a file containing serialized RDF data into a graph, returning
  the graph. The file may be specified using:

  - String URIs,
  - java.net.URI,
  - java.net.URL
  - java.io.File"
  [^Graph graph file]
  (cond
    (string? file) (RDFDataMgr/read ^Graph graph ^String file)
    (uri? file) (RDFDataMgr/read ^Graph graph ^String (str file))
    (instance? java.net.URL file) (RDFDataMgr/read graph (str (.toURI ^URL file)))
    (instance? java.io.File file) (RDFDataMgr/read graph
                                                   (-> ^File file
                                                       (.getAbsoluteFile)
                                                       (.toURI)
                                                       (str))))
  graph)



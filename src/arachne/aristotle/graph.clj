(ns arachne.aristotle.graph
  "Tools for converting Clojure data to an Jena Graph representation"
  (:require [arachne.aristotle.registry :as reg]
            [clojure.spec.alpha :as s])
  (:import [clojure.lang Keyword Symbol]
           [java.net URL URI]
           [java.util GregorianCalendar Calendar Date Map Collection List]
           [org.apache.jena.graph Node NodeFactory Triple GraphUtil Node_URI Node_Literal Node_Variable Node_Blank Factory]
           [org.apache.jena.datatypes.xsd XSDDatatype]
           [javax.xml.bind DatatypeConverter]
           [org.apache.jena.riot RDFDataMgr])
  (:refer-clojure :exclude [reify load]))

(defn variable?
  [s]
  (and (symbol? s) (.startsWith (name s) "?")))

(defn uri-str?
  [o]
  (and (string? o) (re-matches #"^<.*>$" o)))

(defn literal?
  [obj]
  (and (not (coll? obj))
    (not (instance? java.util.Collection obj))))

(s/def ::variable variable?)

(s/def ::iri (s/or :keyword keyword?
               :uri uri-str?))

(s/def ::literal literal?)

(s/def ::node (s/or :variable ::variable
                    :iri     ::iri
                    :literal ::literal))

(s/def ::triple (s/tuple ::node ::node ::node))

(s/def ::triples (s/or :map map?
                       :triples (s/coll-of ::triple :min-count 1)
                       :single-triple ::triple))

(defprotocol AsTriples
  "An object that can be converted to a collection of Jena Triples."
  (triples [obj] "Convert this object to a collection of Jena Triples"))

(defprotocol AsNode
  "An object that can be interpreted as a node in an RDF graph."
  (node [obj] "Convert this object to a Jena RDFNode."))

(defprotocol AsClojureData
  "A Node that can be converted back to Clojure data"
  (data [node] "Convert this node to Clojure data"))

(extend-protocol AsNode
  Keyword
  (node [kw] (NodeFactory/createURI (reg/iri kw)))
  URI
  (node [uri] (NodeFactory/createURI (.toString uri)))
  URL
  (node [url] (NodeFactory/createURI (.toString url)))
  Symbol
  (node [sym]
    (cond
      (= '_ sym) (NodeFactory/createBlankNode)
      (.startsWith (name sym) "?") (NodeFactory/createVariable (subs (name sym) 1))
      :else (NodeFactory/createBlankNode (str sym))))
  String
  (node [obj]
    (if-let [uri (second (re-find #"^<(.*)>$" obj))]
      (NodeFactory/createURI uri)
      (NodeFactory/createLiteralByValue obj XSDDatatype/XSDstring)))
  Long
  (node [obj]
    (NodeFactory/createLiteralByValue obj XSDDatatype/XSDlong))
  Double
  (node [obj]
    (NodeFactory/createLiteralByValue obj XSDDatatype/XSDdouble))
  Boolean
  (node [obj]
    (NodeFactory/createLiteralByValue obj XSDDatatype/XSDboolean))
  java.math.BigDecimal
  (node [obj]
    (NodeFactory/createLiteralByValue obj XSDDatatype/XSDdecimal))
  Date
  (node [obj]
    (node (doto (GregorianCalendar.) (.setTime obj))))
  Calendar
  (node [obj]
    (NodeFactory/createLiteral
      (DatatypeConverter/printDateTime obj)
      XSDDatatype/XSDdateTime))
  Node
  (node [node] node))

(extend-protocol AsClojureData
  Node_URI
  (data [n] (let [uri (.getURI n)]
                 (or (reg/kw uri)
                     (str "<" uri ">"))))
  Node_Literal
  (data [n] (if (= XSDDatatype/XSDdateTime (.getLiteralDatatype n))
                 (.getTime (.asCalendar (.getLiteralValue n)))
                 (.getLiteralValue n)))
  Node_Variable
  (data [n] (symbol (str "?" (.getName n)))))

(defn- triple?
  "Does an object look like a triple?"
  [obj]
  (and (instance? List obj)
       (= 3 (count obj))
       (not-any? coll? obj)))

(defn triple
  "Build a Triple object"
  ([[s p o]] (triple s p o))
  ([s p o] (Triple/create (node s) (node p) (node o))))

(extend-protocol AsTriples

  Triple
  (triples [triple] [triple])

  Collection
  (triples [coll]
    (if (triple? coll)
      [(apply triple (map node coll))]
      (mapcat triples coll)))

  Map
  (triples [m]
    (let [subject (if-let [about (:rdf/about m)]
                    (node about)
                    (NodeFactory/createBlankNode))
          child-map-triples (fn [property child-map]
                              (let [child-triples (triples child-map)
                                    child-subject (.getSubject (first child-triples))]
                                (cons
                                  (triple subject property child-subject)
                                  child-triples)))
          result-triples (mapcat (fn [[k v]]
                                   (cond
                                     (instance? Map v)
                                     (child-map-triples k v)

                                     (instance? Collection v)
                                     (mapcat (fn [child]
                                               (if (instance? Map child)
                                                 (child-map-triples k v)
                                                 [(triple subject k child)])) v)

                                     :else
                                     [(triple subject k v)]))
                           (dissoc m :rdf/about))]
      (with-meta result-triples {::subject subject}))))

(defn reify
  "Given a collection of Triples, reify each triple and return a seq of
   triples explicitly stating the rdf type, subject, predicate and object of
   each input triple."
  [triples]
  (mapcat (fn [t]
            (let [n (NodeFactory/createBlankNode)]
              (arachne.aristotle.graph/triples
                {:rdf/about n
                 :rdf/type :rdf/Statement
                 :rdf/subject (.getSubject t)
                 :rdf/predicate (.getPredicate t)
                 :rdf/object (.getObject t)})))
    triples))

(defn graph
  "Convert the given set of triples to a Jena Graph object, or add them to an existing graph"
  ([triples] (graph (Factory/createDefaultGraph) triples))
  ([graph triples]
   (GraphUtil/add graph triples)
   graph))

(defn load
  "Load a file containing RDF data into a Jena Graph object. Attempts to infer the RDF language.

  Argument may be a string URI, or a URI, URL, or File object."
  [uri]
  (cond
    (string? uri) (RDFDataMgr/loadGraph uri)
    (uri? uri) (load (str uri))
    (instance? java.net.URL uri) (RDFDataMgr/loadGraph (str (.toURI uri)))
    (instance? java.io.File uri) (RDFDataMgr/loadGraph
                                   (-> uri
                                     (.getAbsoluteFile)
                                     (.toURI)
                                     (str)))))

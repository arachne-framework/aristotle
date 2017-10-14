(ns arachne.aristotle.rdf
  "Tools for converting Clojure Data to an RDF representation"
  (:require [arachne.aristotle.registry :as reg])
  (:import [org.apache.jena.rdf.model AnonId ModelFactory Property
                                      Statement Resource RDFNode]
           [clojure.lang Keyword Symbol]
           [java.net URL URI]
           [org.apache.jena.datatypes.xsd XSDDatatype]
           [java.util GregorianCalendar]))

(defprotocol AsStatements
  "An object that can be converted to one or more RDF statements, based on type"
  (statements [obj model] "Convert this object to a sequence of Jena Statements"))

(defprotocol AsResource
  "An object that can be interpreted as an RDF resource in the context of the given Jena model."
  (resource [obj model] "Convert this object to a Jena Resource"))

(defprotocol AsProperty
  "An object that can be interpreted as an RDF property in the context of the given Jena model."
  (property [obj model] "Convert this object to a Jena Property"))

(defprotocol AsNode
  "An object that can be interpreted as an RDF node in the context of the given Jena model."
  (node [obj model] "Convert this object to a Jena RDFNode."))

(defn- triple?
  "Determine if a list looks like a RDF triple."
  [list]
  (and
    (= 3 (count list))
    (satisfies? AsResource (first list))))

(defn- tuple->statement
  "Convert a 3-tuple to an Jena Statement in the given model"
  [[s p o] model]
  (.createStatement model (resource s model) (property p model) (node o model)))

(defn- map->rdf
  "Convert a map to a series of RDF statements. Returns a sequence of Jena
   Statement objects.

   The returned sequence will have metadata with an `::subject` key with a Jena
   resource indicating the subject of each statement.

   The returned sequence may be empty."
  ([m model]
   (let [subject (if-let [about (:rdf/about m)]
                   (resource about model)
                   (.createResource model))
         child-statements (fn [property child]
                            (let [statements (map->rdf child model)
                                  child-subj (::subject (meta statements))]
                              (cons
                                (tuple->statement [subject property child-subj] model)
                                statements)))]
     (with-meta (mapcat (fn [[k v]]
                          (cond

                            (instance? java.util.Map v)
                            (child-statements k v)

                            (instance? java.util.Collection v)
                            (mapcat (fn [child]
                                      (if (map? child)
                                        (child-statements k child)
                                        [(tuple->statement [subject k child] model)]))
                              v)

                            :else
                            [(tuple->statement [subject k v] model)]))
                  (dissoc m :rdf/about))
       {::subject subject}))))

(extend-protocol AsStatements

  java.util.List
  (statements [obj model]
    (if (triple? obj)
      [(tuple->statement obj model)]
      (mapcat #(statements % model) obj)))

  java.util.Map
  (statements [obj model]
    (map->rdf obj model)))

(extend-protocol AsResource
  String
  (resource [uri model] (.createResource model uri))
  Keyword
  (resource [kw model] (resource (reg/iri kw) model))
  Symbol
  (resource [sym model] (.createResource model (AnonId. (str sym))))
  URI
  (resource [uri model] (.createResource model (.toString uri)))
  URL
  (resource [url model] (.createResource model (.toString url)))
  Resource
  (resource [resource model] resource))

(extend-protocol AsProperty
  String
  (property [uri model] (.createProperty model uri))
  Keyword
  (property [kw model] (property (reg/iri kw) model))
  URI
  (property [uri model] (.createProperty model (.toString uri)))
  URL
  (property [url model] (.createProperty model (.toString url)))
  Property
  (property [property model] property))

(extend-protocol AsNode
  Keyword
  (node [kw model] (resource (reg/iri kw) model))
  URI
  (node [uri model] (.createResource model (.toString uri)))
  URL
  (node [url model] (.createResource model (.toString url)))
  Symbol
  (node [sym model] (.createResource model (AnonId. (str sym))))
  String
  (node [obj model] (.createTypedLiteral model obj))
  Long
  (node [obj model] (.createTypedLiteral model obj))
  Double
  (node [obj model] (.createTypedLiteral model obj))
  Boolean
  (node [obj model] (.createTypedLiteral model obj))
  java.math.BigDecimal
  (node [obj model] (.createTypedLiteral model obj))
  java.util.Calendar
  (node [obj model] (.createTypedLiteral model obj))
  java.util.Date
  (node [obj model]
    (node (doto (GregorianCalendar/getInstance)
            (.setTime obj)) model))
  RDFNode
  (node [node model] node))

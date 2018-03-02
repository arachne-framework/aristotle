(ns arachne.aristotle.graph
  "Tools for converting Clojure data to an Jena Graph representation"
  (:require [arachne.aristotle.registry :as reg]
            [clojure.spec.alpha :as s])
  (:import [clojure.lang Keyword Symbol]
           [java.net URL URI]
           [java.util GregorianCalendar Calendar Date Map Collection List]
           [org.apache.jena.graph Node NodeFactory Triple GraphUtil Node_URI Node_Literal Node_Variable Node_Blank Factory Graph]
           [org.apache.jena.datatypes BaseDatatype]
           [org.apache.jena.datatypes.xsd XSDDatatype]
           [javax.xml.bind DatatypeConverter]
           [org.apache.jena.riot RDFDataMgr]
           [org.apache.jena.reasoner TriplePattern])
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

(s/def ::blank (s/or :anonymous #(= '_ %)
                     :named #(and (symbol? %) (.startsWith (name %) "_"))))

(s/def ::node (s/or :variable ::variable
                    :blank    ::blank
                    :iri      ::iri
                    :literal  ::literal))

(s/def ::triple (s/tuple ::node ::node ::node))

(defn graph? [obj] (instance? Graph obj))

(s/def ::triples (s/or :map map?
                       :maps (s/coll-of map? :min-count 1)
                       :triples (s/coll-of ::triple :min-count 1)
                       :single-triple ::triple
                       :graph graph?
                       :empty #(and (coll? %) (empty? %))))

(defprotocol AsTriples
  "An object that can be converted to a collection of Jena Triples."
  (triples [obj] "Convert this object to a collection of Jena Triples"))

(defprotocol AsNode
  "An object that can be interpreted as a node in an RDF graph."
  (node [obj] "Convert this object to a Jena RDFNode."))

(defprotocol AsClojureData
  "A Node that can be converted back to Clojure data"
  (data [node] "Convert this node to Clojure data"))

(def symbol-datatype (proxy [BaseDatatype] ["urn:clojure.org:symbol"]
                       (isValidValue [val] (symbol? val))
                       (getJavaClass [] clojure.lang.Symbol)
                       (parse [lexical-form] (symbol lexical-form))
                       (toString [val] (str val))))

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
      (.startsWith (name sym) "_") (NodeFactory/createBlankNode
                                    (str (symbol (namespace sym)
                                                  (subs (name sym) 1))))
      (.startsWith (name sym) "?") (NodeFactory/createVariable (subs (name sym) 1))
      :else (NodeFactory/createLiteral (str sym) symbol-datatype)))
  String
  (node [obj]
    (if-let [uri (second (re-find #"^<(.*)>$" obj))]
      (NodeFactory/createURI uri)
      (NodeFactory/createLiteralByValue obj XSDDatatype/XSDstring)))
  Long
  (node [obj]
    (NodeFactory/createLiteralByValue obj XSDDatatype/XSDlong))
  Integer
  (node [obj]
    (NodeFactory/createLiteralByValue obj XSDDatatype/XSDlong))
  Double
  (node [obj]
    (NodeFactory/createLiteralByValue obj XSDDatatype/XSDdouble))
  Float
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

(defn- subject-map
  "Given a set of triples with the same subject, emit a Clojure map"
  [subject triples]
  (->> triples
       (group-by #(.getPredicate %))
       (map (fn [[pred triples]]
              (let [objects (map #(data (.getObject %)) triples)]
                [(data pred) (if (= 1 (count objects))
                               (first objects)
                               objects)])))
       (into {:rdf/about (data subject)})))

(defn- graph->clj
  "Convert a Graph to a Clojure data structure"
  [g]
  (->> (iterator-seq (.find g))
       (group-by #(.getSubject %))
       (map (fn [[subject triples]] (subject-map subject triples)))))

(extend-protocol AsClojureData
  nil
  (data [n] nil)
  Node_URI
  (data [n] (let [uri (.getURI n)]
                 (or (reg/kw uri)
                     (str "<" uri ">"))))
  Node_Literal
  (data [n] (if (= XSDDatatype/XSDdateTime (.getLiteralDatatype n))
              (.getTime (.asCalendar (.getLiteralValue n)))
              (.getLiteralValue n)))
  Node_Variable
  (data [n] (symbol (str "?" (.getName n))))

  Node_Blank
  (data [n] (symbol (str "_" (.getLabelString (.getBlankNodeId n)))))

  Graph
  (data [g] (graph->clj g)))

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

  arachne.aristotle.registry.Prefix
  (triples [prefix]
    (reg/install-prefix prefix)
    [])

  Triple
  (triples [triple] [triple])

  Collection
  (triples [coll]
    (reg/with {}
      (if (triple? coll)
        [(apply triple (map node coll))]
        (mapcat triples coll))))

  Map
  (triples [m]
    (reg/with {}
      (let [subject (if-let [about (:rdf/about m)]
                      (node about)
                      (NodeFactory/createBlankNode))
            m (dissoc m :rdf/about)
            m (if (empty? m)
                {:rdf/type :rdfs/Resource}
                m)
            child-map-triples (fn [property child-map]
                                (let [child-triples (triples child-map)
                                      child-subject (.getSubject (first child-triples))]
                                  (cons
                                   (triple subject property child-subject)
                                   child-triples)))]
        (mapcat (fn [[k v]]
                  (cond
                    (instance? Map v)
                    (child-map-triples k v)

                    (instance? Collection v)
                    (mapcat (fn [child]
                              (if (instance? Map child)
                                (child-map-triples k child)
                                [(triple subject k child)])) v)

                    :else
                    [(triple subject k v)]))
                m))))

  Graph
  (triples [g]
    (.toSet (.find g (Triple. (node '?s) (node '?p) (node '?o))))))

(defn reify
  "Given a graph, a property and an object, add reification triples to
  the graph an add a [statement property subject] triple on the
  reified statement."
  [graph property subject]
  (let [new-triples (mapcat (fn [t]
                              (triples {:rdf/type :rdf/Statement
                                        :rdf/subject (.getSubject t)
                                        :rdf/predicate (.getPredicate t)
                                        :rdf/object (.getObject t)
                                        property subject}))
                            (triples graph))]
    (GraphUtil/add ^Graph graph ^java.util.List new-triples))
  graph)

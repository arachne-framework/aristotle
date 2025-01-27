(ns arachne.aristotle.graph
  "Tools for converting Clojure data to an Jena Graph representation"
  (:require [arachne.aristotle.registry :as reg]
            [ont-app.vocabulary.lstr :refer [lang ->LangStr]]
            [clojure.spec.alpha :as s]
            [clojure.string :as str])
  (:import [clojure.lang Keyword Symbol]
           [ont_app.vocabulary.lstr LangStr]
           [java.net URL URI]
           [java.util GregorianCalendar Calendar Date Map Collection List]
           [org.apache.jena.graph Node NodeFactory Triple GraphUtil Node_URI Node_Literal Node_Variable Node_Blank Graph]
           [org.apache.jena.datatypes.xsd XSDDatatype XSDDateTime]
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

(s/def ::named-blank #(and (symbol? %) (.startsWith (name %) "_")))
(s/def ::anon-blank #(= '_ %))

(s/def ::blank (s/or :anonymous ::anon-blank
                     :named ::named-blank))

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
  (node ^Node [obj] "Convert this object to a Jena RDFNode."))

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
      (.startsWith (name sym) "_") (NodeFactory/createBlankNode
                                    (str (symbol (namespace sym)
                                                  (subs (name sym) 1))))
      (.startsWith (name sym) "?") (NodeFactory/createVariable (subs (name sym) 1))
      (namespace sym) (NodeFactory/createURI (str "urn:clojure:" (namespace sym) "/" (name sym)))
      :else (NodeFactory/createURI (str "urn:clojure:" (name sym)))))
  String
  (node [obj]
    (if-let [uri (second (re-find #"^<(.*)>$" obj))]
      (NodeFactory/createURI uri)
      (NodeFactory/createLiteralByValue obj XSDDatatype/XSDstring)))
  LangStr
  (node [obj]
    (NodeFactory/createLiteral (str obj) (lang obj) XSDDatatype/XSDstring))
  Long
  (node [obj]
    (NodeFactory/createLiteralByValue obj XSDDatatype/XSDlong))
  Integer
  (node [obj]
    (NodeFactory/createLiteralByValue obj XSDDatatype/XSDinteger))
  Double
  (node [obj]
    (NodeFactory/createLiteralByValue obj XSDDatatype/XSDdouble))
  Float
  (node [obj]
    (NodeFactory/createLiteralByValue obj XSDDatatype/XSDfloat))
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
       (group-by #(.getPredicate ^Triple %))
       (map (fn [[pred triples]]
              (let [objects (map #(data (.getObject ^Triple %)) triples)]
                [(data pred) (if (= 1 (count objects))
                               (first objects)
                               objects)])))
       (into {:rdf/about (data subject)})))

(defn graph->clj
  "Convert a Graph to a Clojure data structure. Optionally takes a
  filter function to filter maps before returning."
  ([g] (graph->clj g (constantly true)))
  ([^Graph g ffn]
   (->> (iterator-seq (.find g))
        (group-by #(.getSubject ^Triple %))
        (map (fn [[subject triples]] (subject-map subject triples)))
        (filter ffn))))

(extend-protocol AsClojureData
  nil
  (data [n] nil)
  Node_URI
  (data [n] (let [uri (.getURI n)]
              (or (reg/kw uri)
                  (when (str/starts-with? uri "urn:clojure:")
                    (symbol (str/replace uri "urn:clojure:" "")))
                  (str "<" uri ">"))))
  Node_Literal
  (data [^Node_Literal n]
    (if (= XSDDatatype/XSDdateTime (.getLiteralDatatype n))
      (.getTime (.asCalendar ^XSDDateTime (.getLiteralValue n)))
      (if-let [lang (not-empty (.getLiteralLanguage n))]
        (->LangStr (.getLiteralValue n) lang)
        (.getLiteralValue n))))
  Node_Variable
  (data [n] (symbol (str "?" (.getName n))))

  Node_Blank
  (data [n] (symbol (str "_" (.getBlankNodeLabel n))))

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

(defn- inv-triple
  "Create a triple from the given subject, predicate and object,
  inverting the triple if appropriate for the predicate."
  [s p o]
  (if (and (keyword? p) (.startsWith (name p) "_"))
    (let [p (keyword (namespace p) (subs (name p) 1))]
      (triple o p s))
    (triple s p o)))

(defn rdf-list
  "Create an RDF linked list from the given sequence of values"
  [[item & more]]
  {:rdf/type :rdf/List
   :rdf/first item
   :rdf/rest (if (seq more)
               (rdf-list more)
               :rdf/nil)})

(defn- numbered
  "Return an subject with the given items, numbered in order"
  [items]
  (zipmap
    (map #(str "<" (reg/iri (keyword "rdf" (str "_" %))) ">") (range (count items)))
    items))

(defn rdf-bag
  "Create an RDF Bag from the from the given collection of values"
  [items]
  (assoc (numbered items) :rdf/type :rdf/Bag))

(defn rdf-alt
  "Create an RDF Alt from the from the given collection of values"
  [items]
  (assoc (numbered items) :rdf/type :rdf/Alt))

(defn rdf-seq
  "Create an RDF Seq from the from the given collection of values"
  [items]
  (assoc (numbered items) :rdf/type :rdf/Seq))

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
                                      child-subject (.getSubject ^Triple (first child-triples))]
                                  (cons
                                   (inv-triple subject property child-subject)
                                   child-triples)))]
        (mapcat (fn [[k v]]
                  (cond
                    (instance? Map v)
                    (child-map-triples k v)

                    (instance? Collection v)
                    (mapcat (fn [child]
                              (if (instance? Map child)
                                (child-map-triples k child)
                                [(inv-triple subject k child)])) (filter identity v))
                    :else
                    [(inv-triple subject k v)]))
                m))))

  Graph
  (triples [^Graph g]
    (.toSet (.find g (Triple/create (node '?s) (node '?p) (node '?o))))))

(defn reify
  "Given a graph, a property and an object, add reification triples to
  the graph an add a [statement property subject] triple on the
  reified statement."
  [graph property subject]
  (let [new-triples (mapcat (fn [^Triple t]
                              (triples {:rdf/type :rdf/Statement
                                        :rdf/subject (.getSubject t)
                                        :rdf/predicate (.getPredicate t)
                                        :rdf/object (.getObject t)
                                        property subject}))
                            (triples graph))]
    (GraphUtil/add ^Graph graph ^java.util.List new-triples))
  graph)

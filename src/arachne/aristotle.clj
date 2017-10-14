(ns arachne.aristotle
  "Primary API"
  (:require [arachne.aristotle.rdf :as rdf])
  (:import [org.apache.jena.rdf.model AnonId ModelFactory Statement Resource]
           [clojure.lang Keyword Symbol]
           [java.net URL URI]
           [org.apache.jena.datatypes.xsd XSDDatatype]
           [java.util GregorianCalendar]))


(defn rdf
  "Interprets the given Clojure data structure as RDF data and adds it to the given model."
  ([data]
   (rdf data (ModelFactory/createDefaultModel)))
  ([data model]
   (.add model (rdf/statements data model))))


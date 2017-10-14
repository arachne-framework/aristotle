(ns arachne.aristotle
  "Primary API"
  (:require [arachne.aristotle.rdf :as rdf]
            [arachne.aristotle.registry :as reg])
  (:import [org.apache.jena.rdf.model Model AnonId ModelFactory Statement Resource]
           [clojure.lang Keyword Symbol]
           [java.net URL URI]
           [org.apache.jena.datatypes.xsd XSDDatatype]
           [java.util GregorianCalendar]))


(defn rdf
  "Interprets the given Clojure data structure as RDF data and adds it to the
   given model. If no model is supplied, returns a new one.

   Takes an optional final argument `meta-fn`, a function that can be used to
   add statements about statements (using RDF ReifiedStatements).

   `meta-fn` takes a set of Resources, which are ReifiedStatements from the
   originally supplied data, and should return another Clojure data structure
   representing additional RDF data (which can refer to the given statements.)
   "
  ([data]
   (rdf data (ModelFactory/createDefaultModel)))
  ([data model-or-metafn]
   (if (instance? Model model-or-metafn)
     (.add model-or-metafn (rdf/statements data model-or-metafn))
     (rdf data (ModelFactory/createDefaultModel) model-or-metafn)))
  ([data model meta-fn]
   (let [stmts (rdf/statements data model)
         r-stmts (map #(.createReifiedStatement model %) stmts)
         meta-data (meta-fn r-stmts)]
     (.add model
       (concat stmts (rdf/statements meta-data model))))))

(comment

  (reg/prefix :foaf "http://xmlns.com/foaf/0.1/")
  (reg/prefix :cfg "http://arachne-framework.org/config/")

  (def model (rdf [["#luke":foaf/name "Luke"]
                   ["#luke":foaf/age 32]]))

  (def model (rdf [{:rdf/about "#luke"
                    :foaf/name "Luke"
                    :foaf/age 32
                    :foaf/numbers #{32 49 66}
                    :foaf/friend
                    {:rdf/about "#joe"
                     :foaf/name "Joe"
                     :foaf/age 33}
                    :foaf/friends [{:rdf/about "#jim"
                                    :foaf/name "Jim"}
                                   {:rdf/about "#kim"
                                    :foaf/name "Kim"
                                    :foaf/friends {:rdf/about "#jim"}}]}]))

  (def model (rdf [[{:rdf/about "#luke"
                     :foaf/name "Luke"
                     :foaf/age 32}]]
               (fn [stmts]
                 [{:rdf/about "#my-tx"
                   :cfg/file "foo.cj"
                   :cfg/line 42
                   :cfg/statements stmts}])))

  (.size model)

  (let [stmts (iterator-seq (.listStatements model))]
    (zipmap stmts (map #(.isReified %) stmts)))



  (do
    (println "\n\n")
    ;(.write model System/out "N-TRIPLES")
    (.write model System/out "TURTLE")
    (println "\n\n"))

  )
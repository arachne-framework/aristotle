(ns arachne.aristotle.query
  (:require [arachne.aristotle.registry :as reg]
            [arachne.aristotle.query.compiler :as qc]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.locks :as l]
            [clojure.spec.alpha :as s])
  (:import [org.apache.jena.query QueryFactory QueryExecutionFactory]
           [org.apache.jena.sparql.algebra AlgebraGenerator Algebra OpAsQuery Op]
           [org.apache.jena.sparql.algebra.op OpProject Op1]
           [org.apache.jena.rdf.model Model]
           [com.sun.org.apache.xpath.internal.operations Mod]
           [org.apache.jena.graph Graph]
           [org.apache.jena.sparql.engine.binding Binding]))

(defn- find-vars
  "Unwrap the given operation until we find an OpProject, then return the list of vars."
  [op]
  (cond
    (instance? OpProject op) (.getVars ^OpProject op)
    (instance? Op1 op) (recur (.getSubOp ^Op1 op))
    :else nil))

(defn run
  "Given an input Model and an Operation, evaluate the query and return
  the results as a realized Clojure data structure."
  [op model]
  (l/read model
   (let [result-seq (iterator-seq (Algebra/exec ^Op op model))]
     (if-let [vars (find-vars op)]
       (mapv (fn [^Binding binding]
               (mapv #(graph/data (.get binding %)) vars))
             result-seq)
       (mapv (fn [^Binding binding]
               (into {}
                     (for [var (iterator-seq (.vars binding))]
                       [(graph/data var) (graph/data (.get binding var))])))
             result-seq)))))

(defn build
  "Build a Jena Operation object from the given query, represented as a
   Clojure data structure"
  [query]
  (let [op (qc/op query)
        op (Algebra/optimize op)]
    op))

(defn sparql
  "Return a SPARQL query string for the given Jena Operation (as returned from `build`).
  Useful mostly for debugging."
  [op]
  (OpAsQuery/asQuery op))

(defn parse
  "Parse a SPARQL query string into a Jena Operation"
  [query-str]

  (let [q (QueryFactory/create query-str)]
    (-> (AlgebraGenerator.)
        (.compile q)
        (Algebra/optimize))))

(defn query
  "Build and execute a query on the given model."
  [query model]
  (run (build query) model))

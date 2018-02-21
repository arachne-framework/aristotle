(ns arachne.aristotle.query
  (:require [arachne.aristotle.registry :as reg]
            [arachne.aristotle.query.compiler :as qc]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.locks :as l]
            [arachne.aristotle.query.spec :as qs]
            [clojure.spec.alpha :as s]
            [clojure.walk :as w])
  (:import [org.apache.jena.query QueryFactory QueryExecutionFactory]
           [org.apache.jena.sparql.algebra AlgebraGenerator Algebra OpAsQuery Op]
           [org.apache.jena.sparql.algebra.op OpProject Op1 OpSequence]
           [org.apache.jena.rdf.model Model]
           [com.sun.org.apache.xpath.internal.operations Mod]
           [org.apache.jena.graph Graph]
           [org.apache.jena.sparql.engine.binding Binding]))


(s/def ::run-args (s/cat :bindings (s/? (s/coll-of ::graph/variable))
                         :query (s/or :op #(instance? Op %)
                                      :query ::qs/operation)
                         :model #(instance? Model %)
                         :data (s/? ::qs/bindings)))

(defn build
  "Build a Jena Operation object from the given query, represented as a
   Clojure data structure"
  [query]
  (let [op (qc/op query)
        op (Algebra/optimize op)]
    op))

(defn- bind-data
  "Wrap the given operation in an OpTable, establishing initial
  bindings for the vars in the data map."
  [op data]
  (OpSequence/create
   (qc/build-table data)
   op))

(defn- project
  "Wrap the operation in a projection over the specified vars."
  [op binding-vars]
  (OpProject. op (qc/var-seq binding-vars)))


(defn run
  "Given a model and a query (which may be either a precompiled instance
  of org.apache.sparql.algebra.Op, or a Query data structure), execute
  the query and return results.

  Results will be returned as a sequence of maps of variable bindings,
  unless an optional binding vector is passed as the first
  argument. If it is, results are returned as a set of vectors.

  Takes an optional final argument which is a map of initial variable
  bindings. This is how parameterized inputs are passed into the
  query."
;  {:arglists '[bindings? model query data?]}
  [& args]
  (let [{:keys [bindings model query data] :as r} (s/conform ::run-args args)
        _ (when (= r ::s/invalid) (s/assert* ::run-args args))
        operation (if (= :op (first query))
                    (second query)
                    (build (w/prewalk identity (s/unform ::qs/operation (second query)))))
        data (when data (map second data))
        operation (if data (bind-data operation data) operation)
        binding-vars (when bindings (qc/var-seq bindings))
        operation (if binding-vars (project operation binding-vars) operation)
        result-seq (iterator-seq (Algebra/exec ^Op operation ^Model model))]
    (if binding-vars
      (into #{} (map (fn [^Binding binding]
                       (mapv #(graph/data (.get binding %)) binding-vars))
                     result-seq))
      (mapv (fn [^Binding binding]
                  (into {}
                        (for [var (iterator-seq (.vars binding))]
                          [(graph/data var) (graph/data (.get binding var))])))
            result-seq))))

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

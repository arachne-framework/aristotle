(ns arachne.aristotle.query
  (:require [arachne.aristotle.registry :as reg]
            [arachne.aristotle.query.compiler :as qc]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.query.spec :as qs]
            [clojure.spec.alpha :as s]
            [clojure.walk :as w])
  (:import [org.apache.jena.query QueryFactory QueryExecutionFactory]
           [org.apache.jena.sparql.algebra AlgebraGenerator Algebra OpAsQuery Op]
           [org.apache.jena.sparql.algebra.op OpProject Op1 OpSequence]
           [com.sun.org.apache.xpath.internal.operations Mod]
           [org.apache.jena.graph Graph Triple Node]
           [org.apache.jena.sparql.engine.binding Binding]))


(s/def ::run-args (s/cat :graph #(instance? Graph %)
                         :bindings (s/? (s/coll-of ::graph/variable))
                         :query (s/or :op #(instance? Op %)
                                      :query ::qs/operation)
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
  "Given a graph and a query (which may be either a precompiled instance
  of org.apache.sparql.algebra.Op, or a Query data structure), execute
  the query and return results.

  Results will be returned as a sequence of maps of variable bindings,
  unless an optional binding vector is passed as the first
  argument. If it is, results are returned as a set of vectors.

  Takes an optional final argument which is a map of initial variable
  bindings. This is how parameterized inputs are passed into the
  query."
  [& args]
  (let [{:keys [bindings graph query data] :as r} (s/conform ::run-args args)
        _ (when (= r ::s/invalid) (s/assert* ::run-args args))
        operation (if (= :op (first query))
                    (second query)
                    (build (w/prewalk identity (s/unform ::qs/operation (second query)))))
        data (when data (map second data))
        operation (if data (bind-data operation data) operation)
        binding-vars (when bindings (qc/var-seq bindings))
        operation (if binding-vars (project operation binding-vars) operation)
        result-seq (iterator-seq (Algebra/exec ^Op operation ^Graph graph))]
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
  [^String query-str]
  (let [q (QueryFactory/create query-str)]
    (-> (AlgebraGenerator.)
        (.compile q)
        (Algebra/optimize))))


(s/def ::pull-pattern
  (s/coll-of ::pull-attr :min-count 1))

(s/def ::pull-attr
  (s/or :wildcard #{'*}
        :attr-name ::graph/iri
        :map ::pull-map))

(s/def ::pull-map
  (s/map-of ::graph/iri (s/or :pattern ::pull-pattern
                              :recur #{'...}
                              :recur-n int?)
            :conform-keys true))

(s/conform ::pull-pattern [:a/b '* :c/d {:foo/bar [:x/y]}])

(def ^:private pull-q
  (build '[:conditional
           [:bgp [?subj ?pred ?obj]]
           [:sequence
            [:bgp
             [?subj :rdf/type ?class]
             [?class :owl/onProperty ?pred]]
            [:disjunction
             [:bgp [?class :owl/cardinality 1]]
             [:bgp [?class :owl/maxCardinality 1]]]]]))

(declare pull*)

(defn- compile-pattern
  "Compile a pattern to a function."
  [pattern]
  (let [pattern (set pattern)
        wild? (pattern '*)
        keys (->> pattern
                  (mapcat #(cond (map? %) (keys %)
                                 (= '* %) []
                                 :else [%]))
                  set)
        subs (->> pattern
                  (filter map?)
                  (apply merge)
                  (map (fn [[k v]]
                         (when (vector? v) [k (compile-pattern v)])))
                  (into {}))
        limits (->> pattern
                    (filter map?)
                    (apply merge)
                    (map (fn [[k v]]
                           (cond
                             (int? v) [k v]
                             (= '... v) [k Long/MAX_VALUE]
                             :else nil)))
                    (into {}))]
    (fn parse-val [graph depth pred val]
      (when (or wild? (keys pred))
        (let [subparser (subs pred)
              limit (limits pred)]
          (cond
            subparser (pull* graph val subparser 0)
            limit (if (<= limit depth)
                    val
                    (pull* graph val parse-val (inc depth)))
            :else val))))))

(defn- pull*
  [graph subject parse-val depth]
  (let [results (run graph pull-q {'?subj subject})]
     (when-not (empty? results)
       (reduce (fn [acc {card1 '?class, pred '?pred, val '?obj}]
                 (if-let [val (parse-val graph depth pred val)]
                   (if card1
                     (assoc acc pred val)
                     (update acc pred (fnil conj #{}) val))
                   acc))
               {:rdf/about subject} results))))

(defn pull
  "Get all the properties associated with a subject using syntax similar
  to Datomic's Pull.

  Cardinality-1 properties will be returned as single values,
  otherwise property values will be wrapped in sets. Graph must
  support OWL inferencing to make this determination."
  [graph subject pattern]
  (pull* graph subject (compile-pattern pattern) 0))

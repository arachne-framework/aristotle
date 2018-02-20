(ns arachne.aristotle.validation
  "Utils for returning inference validation errors in a consistent way"
  (:require [arachne.aristotle :as a]
            [arachne.aristotle.graph :as g]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.query :as q])
  (:import [org.apache.jena.rdf.model InfModel]
           [org.apache.jena.reasoner ValidityReport ValidityReport$Report]))


(defn built-in
  "Validator which discovers any validation errors returned by the
  Reasoner itself"
  [^InfModel m]
  (let [r (.validate m)]
    (if (.isValid r)
      []
      (map (fn [^ValidityReport$Report r]
             {::error? (boolean (.isError r))
              ::type :inference
              ::jena-type (.getType r)
              ::description (.getDescription r)})
           (iterator-seq (.getReports r))))))

(let [q (q/build
         '[:filter (< ?actual ?expected)
           [:group [?c ?e ?p ?expected] [?actual (count ?val)]
            [:join
             [:disjunction
              [:bgp [?c :owl/cardinality ?expected]]
              [:bgp [?c :owl/minCardinality ?expected]]]
             [:conditional
              [:bgp
               [?c :owl/onProperty ?p]
               [?e :rdf/type ?c]]
              [:bgp [?e ?p ?val]]]]]])]

  (defn min-cardinality
    "Return a validation error for all entities that do not conform to any
  minCardinality restrictions on their parent classes.

   This validator is only correct when using Jena's Owl mini
  reasoner. The full reasoner uses minCardinality to infer the
  existence of blank nodes as values of a minCardinality property
  which while technically valid is not helpful for determining if
  something is logically missing."
    [m]
    (mapv (fn [[entity property expected actual]]
           {::error? true
            ::type ::min-cardinality
            ::description (format "Min-cardinality violation on %s. Expected at least %s distinct values for property %s, got %s"
                                  entity expected property actual)
            ::details {:entity entity
                       :property property
                       :expected expected
                       :actual actual}})
         (q/run '[?e ?p ?expected ?actual] q m))))

(defn validate
  "Validate the given model, returning a sequence of validation errors
  or warnings. Always returns validation errors from the internal
  reaswoner's own consistency checks, as well as any additional
  validators provided.

  Custom validators are functions which take a model and return a
  collection of maps, each representing a validation error or
  warining.

  Unlike the built-in validators, custom validators may peform
  arbitrary logic (i.e, perform validations such as minCardinality
  that require a closed-world reasoning model instaed of OWL's
  open-world default.)"
  ([m] (validate m []))
  ([m validators]
   (mapcat #(% m) (conj validators built-in))))

(ns arachne.aristotle.validation
  "Utils for returning inference validation errors in a consistent way"
  (:require [arachne.aristotle :as a]
            [arachne.aristotle.graph :as g]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.query :as q])
  (:import [org.apache.jena.rdf.model InfModel]
           [org.apache.jena.reasoner ValidityReport ValidityReport$Report]))


(defn built-in
  "Discover any validation errors returned by the Reasoner itself"
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

(def default-validators
  #{built-in})

(defn validate
  "Validate the given model, returning a sequence of validation errors
  or warnings. Uses the default validators
 (arachne.aristotle.validation/default-validators), or optionally
  takes a collection of custom validators.

   Custom validators are functions which take a model and return a
  collection of maps, each representing a validation error or
  warning.

  Note: unlike built-in OWL inference, some of these validators may
  'close the world' and assert things like minCardinality that can't
  properly be enforced using open world reasoning.

  Default validators are restricted to open-world reasoning (i.e,
  patching holes from Jena.)"
  ([m] (validate m default-validators))
  ([m validators]
   (mapcat #(% m) validators)))
